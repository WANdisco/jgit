/*
 * Copyright (C) 2017, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;
import static org.eclipse.jgit.util.ReplicationConfiguration.shouldReplicateRepository;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wandisco.gerrit.gitms.shared.api.repository.BatchGitUpdateResult;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.RefDirectory.PackedRefList;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RefList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link BatchRefUpdate} that uses the {@code packed-refs}
 * file to support atomically updating multiple refs.
 * <p>
 * The algorithm is designed to be compatible with traditional single ref
 * updates operating on single refs only. Regardless of success or failure, the
 * results are atomic: from the perspective of any reader, either all updates in
 * the batch will be visible, or none will. In the case of process failure
 * during any of the following steps, removal of stale lock files is always
 * safe, and will never result in an inconsistent state, although the update may
 * or may not have been applied.
 * <p>
 * The algorithm is:
 * <ol>
 * <li>Pack loose refs involved in the transaction using the normal pack-refs
 * operation. This ensures that creating lock files in the following step
 * succeeds even if a batch contains both a delete of {@code refs/x} (loose) and
 * a create of {@code refs/x/y}.</li>
 * <li>Create locks for all loose refs involved in the transaction, even if they
 * are not currently loose.</li>
 * <li>Pack loose refs again, this time while holding all lock files (see {@link
 * RefDirectory#pack(Map)}), without deleting them afterwards. This covers a
 * potential race where new loose refs were created after the initial packing
 * step. If no new loose refs were created during this race, this step does not
 * modify any files on disk. Keep the merged state in memory.</li>
 * <li>Update the in-memory packed refs with the commands in the batch, possibly
 * failing the whole batch if any old ref values do not match.</li>
 * <li>If the update succeeds, lock {@code packed-refs} and commit by atomically
 * renaming the lock file.</li>
 * <li>Delete loose ref lock files.</li>
 * </ol>
 * <p>
 * Because the packed-refs file format is a sorted list, this algorithm is
 * linear in the total number of refs, regardless of the batch size. This can be
 * a significant slowdown on repositories with large numbers of refs; callers
 * that prefer speed over atomicity should use {@code setAtomic(false)}. As an
 * optimization, an update containing a single ref update does not use the
 * packed-refs protocol.
 */
class PackedBatchRefUpdate extends BatchRefUpdate {

	private final static Logger logger = LoggerFactory
			.getLogger(PackedBatchRefUpdate.class);

	private RefDirectory refdb;

	PackedBatchRefUpdate(RefDirectory refdb) {
		super(refdb);
		this.refdb = refdb;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(RevWalk walk, ProgressMonitor monitor,
						List<String> options) throws IOException {
		if (!isAtomic()) {
			// Use default one-by-one implementation.
			super.execute(walk, monitor, options);
			return;
		}

		// get the username for this thread context before we do anything else.
		String user = RefUpdate.getUsernameAndClear();

		List<ReceiveCommand> pending =
				ReceiveCommand.filter(getCommands(), NOT_ATTEMPTED);
		if (pending.isEmpty()) {
			return;
		}
		if (pending.size() == 1) {
			// Single-ref updates are always atomic, no need for packed-refs.
			// Set username as we just cleared it above for the single RefUpdate
			RefUpdate.setUsername(user);
			super.execute(walk, monitor, options);
			return;
		}
		if (containsSymrefs(pending)) {
			// packed-refs file cannot store symrefs
			reject(pending.get(0), REJECTED_OTHER_REASON,
				   JGitText.get().atomicSymRefNotSupported, pending);
			return;
		}

		// Required implementation details copied from super.execute.
		if (!blockUntilTimestamps(MAX_WAIT)) {
			return;
		}
		if (options != null) {
			setPushOptions(options);
		}
		// End required implementation details.

		// Check for conflicting names before attempting to acquire locks, since
		// lockfile creation may fail on file/directory conflicts.
		if (!checkConflictingNames(pending)) {
			return;
		}

		if (!checkObjectExistence(walk, pending)) {
			return;
		}

		if (!checkNonFastForwards(walk, pending)) {
			return;
		}

		// If we are in replicated system and someone hasn't disabled replication for this single call
		// replicate the update now.
		if (replicated && shouldReplicateRepository(refdb.getRepository())) {
			// make sure to replicate this change to other systems in an atomic all or nothing operation.
			if (!replicatedBatchRefUpdate(walk, pending, user)) {
				return;
			}
		} else if (!unreplicatedBatchRefUpdate(walk, pending)) {
			// something went wrong in validation / locking - return early its a failure.
			return;
		}


		refdb.fireRefsChanged();
		pending.forEach(c -> c.setResult(ReceiveCommand.Result.OK));
		writeReflog(pending);
	}

	private boolean unreplicatedBatchRefUpdate(RevWalk walk, List<ReceiveCommand> pending) throws IOException {
		// Pack refs normally, so we can create lock files even in the case where
		// refs/x is deleted and refs/x/y is created in this batch.
		try {
			refdb.pack(
					pending.stream().map(ReceiveCommand::getRefName).collect(toList()));
		} catch (LockFailedException e) {
			lockFailure(pending.get(0), pending);
			return false;
		}

		Map<String, LockFile> locks = null;
		refdb.inProcessPackedRefsLock.lock();
		try {
			PackedRefList oldPackedList;
			if (!refdb.isInClone()) {
				locks = lockLooseRefs(pending);
				if (locks == null) {
					return false;
				}
				oldPackedList = refdb.pack(locks);
			} else {
				// During clone locking isn't needed since no refs exist yet.
				// This also helps to avoid problems with refs only differing in
				// case on a case insensitive filesystem (bug 528497)
				oldPackedList = refdb.getPackedRefs();
			}
			RefList<Ref> newRefs = applyUpdates(walk, oldPackedList, pending);
			if (newRefs == null) {
				return false;
			}
			LockFile packedRefsLock = refdb.lockPackedRefs();
			if (packedRefsLock == null) {
				lockFailure(pending.get(0), pending);
				return false;
			}
			// commitPackedRefs removes lock file (by renaming over real file).
			refdb.commitPackedRefs(packedRefsLock, newRefs, oldPackedList,
								   true);
		} finally {
			try {
				unlockAll(locks);
			} finally {
				refdb.inProcessPackedRefsLock.unlock();
			}
		}
		// all worked as expected return true to continue...
		return true;
	}

	/**
	 * Replicate this batch of reference updates as a single atomic operation to GitMs.
	 *
	 * @param walk    Repo context info
	 * @param pending Pending commands to be issued against the repo.
	 * @param user    Optional argument if supplied, sets up the user context for each of the commands being issued.
	 * @return True if the replication worked and it got results back .
	 * @throws IOException
	 */
	private boolean replicatedBatchRefUpdate(RevWalk walk, List<ReceiveCommand> pending, final String user) throws
			IOException {

		// Go through all the commands and create a list of commands yet to be attempted.
		List<ReceiveCommand> toBeReplicatedCommands =
				ReceiveCommand.filter(pending, NOT_ATTEMPTED);

		// Now we have a list of update requests with packfiles make an atomic operation to commit the entire list of
		// them.
		if (toBeReplicatedCommands.isEmpty()) {
			// we have nothing to do - something is wrong as the calling code ive seen all have isEmpty protection
			// before here....
			logger.warn("Empty batch of packed ref updates found original command size: {}  filtered size: {}, return nothing to do.",
					pending.size(), toBeReplicatedCommands.size());
			return false;
		}

		// Send Atomic proposal now, and send over our list of packfile info.
		BatchGitUpdateResult results =
				ReplicatedUpdate
						.replicatedBatchUpdate(user, toBeReplicatedCommands, refdb.getRepository());

		return results != null;
	}

	private static boolean containsSymrefs(List<ReceiveCommand> commands) {
		for (ReceiveCommand cmd : commands) {
			if (cmd.getOldSymref() != null || cmd.getNewSymref() != null) {
				return true;
			}
		}
		return false;
	}

	private boolean checkConflictingNames(List<ReceiveCommand> commands)
			throws IOException {
		Set<String> takenNames = new HashSet<>();
		Set<String> takenPrefixes = new HashSet<>();
		Set<String> deletes = new HashSet<>();
		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE) {
				takenNames.add(cmd.getRefName());
				addPrefixesTo(cmd.getRefName(), takenPrefixes);
			} else {
				deletes.add(cmd.getRefName());
			}
		}
		Set<String> initialRefs = refdb.getRefs(RefDatabase.ALL).keySet();
		for (String name : initialRefs) {
			if (!deletes.contains(name)) {
				takenNames.add(name);
				addPrefixesTo(name, takenPrefixes);
			}
		}

		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE &&
				takenPrefixes.contains(cmd.getRefName())) {
				// This ref is a prefix of some other ref. This check doesn't apply when
				// this command is a delete, because if the ref is deleted nobody will
				// ever be creating a loose ref with that name.
				lockFailure(cmd, commands);
				return false;
			}
			for (String prefix : getPrefixes(cmd.getRefName())) {
				if (takenNames.contains(prefix)) {
					// A prefix of this ref is already a refname. This check does apply
					// when this command is a delete, because we would need to create the
					// refname as a directory in order to create a lockfile for the
					// to-be-deleted ref.
					lockFailure(cmd, commands);
					return false;
				}
			}
		}
		return true;
	}

	private boolean checkObjectExistence(RevWalk walk,
										 List<ReceiveCommand> commands) throws IOException {
		for (ReceiveCommand cmd : commands) {
			try {
				if (!cmd.getNewId().equals(ObjectId.zeroId())) {
					walk.parseAny(cmd.getNewId());
				}
			} catch (MissingObjectException e) {
				// ReceiveCommand#setResult(Result) converts REJECTED to
				// REJECTED_NONFASTFORWARD, even though that result is also used for a
				// missing object. Eagerly handle this case so we can set the right
				// result.
				reject(cmd, ReceiveCommand.Result.REJECTED_MISSING_OBJECT, commands);
				return false;
			}
		}
		return true;
	}

	private boolean checkNonFastForwards(RevWalk walk,
										 List<ReceiveCommand> commands) throws IOException {
		if (isAllowNonFastForwards()) {
			return true;
		}
		for (ReceiveCommand cmd : commands) {
			cmd.updateType(walk);
			if (cmd.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
				reject(cmd, REJECTED_NONFASTFORWARD, commands);
				return false;
			}
		}
		return true;
	}

	/**
	 * Lock loose refs corresponding to a list of commands.
	 *
	 * @param commands commands that we intend to execute.
	 * @return map of ref name in the input commands to lock file. Always contains
	 * one entry for each ref in the input list. All locks are acquired
	 * before returning. If any lock was not able to be acquired: the
	 * return value is null; no locks are held; and all commands that were
	 * pending are set to fail with {@code LOCK_FAILURE}.
	 * @throws IOException an error occurred other than a failure to acquire; no locks are
	 *                     held if this exception is thrown.
	 */
	@Nullable
	private Map<String, LockFile> lockLooseRefs(List<ReceiveCommand> commands)
			throws IOException {
		ReceiveCommand failed = null;
		Map<String, LockFile> locks = new HashMap<>();
		try {
			RETRY:
			for (int ms : refdb.getRetrySleepMs()) {
				failed = null;
				// Release all locks before trying again, to prevent deadlock.
				unlockAll(locks);
				locks.clear();
				RefDirectory.sleep(ms);

				for (ReceiveCommand c : commands) {
					String name = c.getRefName();
					LockFile lock = new LockFile(refdb.fileFor(name));
					if (locks.put(name, lock) != null) {
						throw new IOException(
								MessageFormat.format(JGitText.get().duplicateRef, name));
					}
					if (!lock.lock()) {
						failed = c;
						continue RETRY;
					}
				}
				Map<String, LockFile> result = locks;
				locks = null;
				return result;
			}
		} finally {
			unlockAll(locks);
		}
		lockFailure(failed != null ? failed : commands.get(0), commands);
		return null;
	}

	/**
	 * WD:  This is a complicated method and only works because of the sorting applied to both lists.
	 * Note it walks through both lists in a lexigraphical sort.
	 * Its not look at every ref for a given cmd, or look at every cmd for a ref.. its go through both simulataneously
	 * to make it faster but this makes assumptions about naming of the paths...
	 * <p>
	 * If cmp < 0
	 * This means the ref is earlier in the list go onto next ref we can go onto next.
	 * OR
	 * This also happens when its a delete operation - i.e. there is not ref there for a given cmd..(ref==null)
	 * If cmp > 0
	 * This means the cmd doesn't exist i.e. (cmd==null) for this ref - its a create operation.
	 * If cmp == 0
	 * This means we have 2 refs which match - go ahead and compare their content.
	 *
	 * @param walk     Repo information
	 * @param refs     RefsDB current refs list
	 * @param commands Batch of ReceiveCommands to be issued.
	 * @return New list of refDB references to be applied to disk.
	 * @throws IOException
	 */
	private static RefList<Ref> applyUpdates(RevWalk walk, RefList<Ref> refs,
											 List<ReceiveCommand> commands) throws IOException {
		// Construct a new RefList by merging the old list with the updates.
		// This assumes that each ref occurs at most once as a ReceiveCommand.
		Collections.sort(commands, new Comparator<ReceiveCommand>() {
			@Override
			public int compare(ReceiveCommand a, ReceiveCommand b) {
				return a.getRefName().compareTo(b.getRefName());
			}
		});

		int delta = 0;
		for (ReceiveCommand c : commands) {
			switch (c.getType()) {
				case DELETE:
					delta--;
					break;
				case CREATE:
					delta++;
					break;
				default:
			}
		}

		RefList.Builder<Ref> b = new RefList.Builder<>(refs.size() + delta);
		int refIdx = 0;
		int cmdIdx = 0;
		// 2 new counters to allow for idempotent retry operations...
		// If we retry in gitms. we wish for the batch operations to complete.. This wasn't allowed here previously,
		// as the operation either applied once or not (atomically), and then all others attempts would fail
		// as they can't lock the old initial state.  We want to allow this ONLY if all the items are either in the
		// old state and moving to the new state, or all items are in the new state and its a NO_CHANGE across entire
		// atomic operation set.
		int matchesOldState = 0;
		int matchesNewState = 0;
		ReceiveCommand failureCmd = null;
		while (refIdx < refs.size() || cmdIdx < commands.size()) {
			Ref ref = (refIdx < refs.size()) ? refs.get(refIdx) : null;
			ReceiveCommand cmd = (cmdIdx < commands.size())
								 ? commands.get(cmdIdx)
								 : null;
			int cmp = 0;
			if (ref != null && cmd != null) {
				cmp = ref.getName().compareTo(cmd.getRefName());
			} else if (ref == null) {
				cmp = 1;
			} else if (cmd == null) {
				cmp = -1;
			}

			if (cmp < 0) {
				b.add(ref);
				refIdx++;
			} else if (cmp > 0) {
				assert cmd != null;
				if (cmd.getType() != ReceiveCommand.Type.CREATE) {
					lockFailure(cmd, commands);
					return null;
				}

				b.add(peeledRef(walk, cmd));
				cmdIdx++;
				// Create state - finds no matching ref, so must be in the old state.
				matchesOldState++;
			} else {
				assert cmd != null;
				assert ref != null;
				if (!cmd.getOldId().equals(ref.getObjectId())) {
					// Before we give up, lets check if its in the new state first.
					if (cmd.getNewId().equals(ref.getObjectId())) {
						// We are already in this state... Just add the ref as is to the list, and bump counter.
						matchesNewState++;
						// take a copy of the first failure command, if we find a mismatch at the end we want to throw
						// the first which would have failed just as before our change...
						if (failureCmd == null) {
							failureCmd = cmd;
						}
					} else {
						lockFailure(cmd, commands);
						return null;
					}
				} else {
					matchesOldState++;
				}

				if (cmd.getType() != ReceiveCommand.Type.DELETE) {
					b.add(peeledRef(walk, cmd));
				}
				cmdIdx++;
				refIdx++;
			}
		}

		// Now before we accept this list, we may be raised a lock failure if for some reason we dont have all of our
		// commands present as either all new or all old state.
		if (matchesNewState > 0 && matchesOldState > 0 ||
			(!(matchesNewState == commands.size() || matchesOldState == commands.size()))) {
			logger.error("applyUpdates: Applying refDB updates failed due to having mixed repository state.  " +
						 "NumCommands: %d, NumMatchingOldState: %d, NumMatchingNewState: %d", commands.size(),
						 matchesOldState, matchesNewState);
			assert failureCmd != null;
			lockFailure(failureCmd, commands);
			return null;
		}

		logger.debug("applyUpdates: Applying refDB updates succeeded" +
					 " NumCommands: %d, NumMatchingOldState: %d, NumMatchingNewState: %d",
					 commands.size(), matchesOldState, matchesNewState);
		return b.toRefList();
	}

	private void writeReflog(List<ReceiveCommand> commands) {
		PersonIdent ident = getRefLogIdent();
		if (ident == null) {
			ident = new PersonIdent(refdb.getRepository());
		}
		for (ReceiveCommand cmd : commands) {
			// Assume any pending commands have already been executed atomically.
			if (cmd.getResult() != ReceiveCommand.Result.OK) {
				continue;
			}
			String name = cmd.getRefName();

			if (cmd.getType() == ReceiveCommand.Type.DELETE) {
				try {
					RefDirectory.delete(refdb.logFor(name), RefDirectory.levelsIn(name));
				} catch (IOException e) {
					// Ignore failures, see below.
				}
				continue;
			}

			if (isRefLogDisabled(cmd)) {
				continue;
			}

			String msg = getRefLogMessage(cmd);
			if (isRefLogIncludingResult(cmd)) {
				String strResult = toResultString(cmd);
				if (strResult != null) {
					msg = msg.isEmpty()
						  ? strResult : msg + ": " + strResult; //$NON-NLS-1$
				}
			}
			try {
				new ReflogWriter(refdb, isForceRefLog(cmd))
						.log(name, cmd.getOldId(), cmd.getNewId(), ident, msg);
			} catch (IOException e) {
				// Ignore failures, but continue attempting to write more reflogs.
				//
				// In this storage format, it is impossible to atomically write the
				// reflog with the ref updates, so we have to choose between:
				// a. Propagating this exception and claiming failure, even though the
				//    actual ref updates succeeded.
				// b. Ignoring failures writing the reflog, so we claim success if and
				//    only if the ref updates succeeded.
				// We choose (b) in order to surprise callers the least.
				//
				// Possible future improvements:
				// * Log a warning to a logger.
				// * Retry a fixed number of times in case the error was transient.
			}
		}
	}

	private String toResultString(ReceiveCommand cmd) {
		switch (cmd.getType()) {
			case CREATE:
				return ReflogEntry.PREFIX_CREATED;
			case UPDATE:
				// Match the behavior of a single RefUpdate. In that case, setting the
				// force bit completely bypasses the potentially expensive isMergedInto
				// check, by design, so the reflog message may be inaccurate.
				//
				// Similarly, this class bypasses the isMergedInto checks when the force
				// bit is set, meaning we can't actually distinguish between UPDATE and
				// UPDATE_NONFASTFORWARD when isAllowNonFastForwards() returns true.
				return isAllowNonFastForwards()
					   ? ReflogEntry.PREFIX_FORCED_UPDATE : ReflogEntry.PREFIX_FAST_FORWARD;
			case UPDATE_NONFASTFORWARD:
				return ReflogEntry.PREFIX_FORCED_UPDATE;
			default:
				return null;
		}
	}

	private static Ref peeledRef(RevWalk walk, ReceiveCommand cmd)
			throws IOException {
		ObjectId newId = cmd.getNewId().copy();
		RevObject obj = walk.parseAny(newId);
		if (obj instanceof RevTag) {
			return new ObjectIdRef.PeeledTag(
					Ref.Storage.PACKED, cmd.getRefName(), newId, walk.peel(obj).copy());
		}
		return new ObjectIdRef.PeeledNonTag(
				Ref.Storage.PACKED, cmd.getRefName(), newId);
	}

	private static void unlockAll(@Nullable Map<?, LockFile> locks) {
		if (locks != null) {
			locks.values().forEach(LockFile::unlock);
		}
	}

	private static void lockFailure(ReceiveCommand cmd,
									List<ReceiveCommand> commands) {
		reject(cmd, LOCK_FAILURE, commands);
	}

	private static void reject(ReceiveCommand cmd, ReceiveCommand.Result result,
							   List<ReceiveCommand> commands) {
		reject(cmd, result, null, commands);
	}

	private static void reject(ReceiveCommand cmd, ReceiveCommand.Result result,
							   String why, List<ReceiveCommand> commands) {
		cmd.setResult(result, why);
		for (ReceiveCommand c2 : commands) {
			if (c2.getResult() == ReceiveCommand.Result.OK) {
				// Undo OK status so ReceiveCommand#abort aborts it. Assumes this method
				// is always called before committing any updates to disk.
				c2.setResult(ReceiveCommand.Result.NOT_ATTEMPTED);
			}
		}
		ReceiveCommand.abort(commands);
	}
}
