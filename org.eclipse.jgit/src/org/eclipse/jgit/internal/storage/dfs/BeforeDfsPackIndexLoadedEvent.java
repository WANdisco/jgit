/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.events.RepositoryEvent;

/**
 * Describes the {@link org.eclipse.jgit.internal.storage.dfs.DfsPackFile} just
 * before its index is loaded. Currently, DfsPackFile directly dispatches the
 * event on {@link org.eclipse.jgit.lib.Repository#getGlobalListenerList}. Which
 * means the call to {@link #getRepository} will always return null.
 */
public class BeforeDfsPackIndexLoadedEvent
		extends RepositoryEvent<BeforeDfsPackIndexLoadedListener> {
	private final DfsPackFile pack;

	/**
	 * A new event triggered before a PackFile index is loaded.
	 *
	 * @param pack
	 *            the pack
	 */
	public BeforeDfsPackIndexLoadedEvent(DfsPackFile pack) {
		this.pack = pack;
	}

	/**
	 * Get the PackFile containing the index that will be loaded.
	 *
	 * @return the PackFile containing the index that will be loaded.
	 */
	public DfsPackFile getPackFile() {
		return pack;
	}

	/** {@inheritDoc} */
	@Override
	public Class<BeforeDfsPackIndexLoadedListener> getListenerType() {
		return BeforeDfsPackIndexLoadedListener.class;
	}

	/** {@inheritDoc} */
	@Override
	public void dispatch(BeforeDfsPackIndexLoadedListener listener) {
		listener.onBeforeDfsPackIndexLoaded(this);
	}
}
