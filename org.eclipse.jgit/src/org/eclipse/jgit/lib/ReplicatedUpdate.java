/********************************************************************************
 * Copyright (c) 2014-2024 WANdisco
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 ********************************************************************************/

package org.eclipse.jgit.lib;

import com.wandisco.gerrit.gitms.shared.api.exceptions.GitUpdateException;
import com.wandisco.gerrit.gitms.shared.api.repository.BatchGitUpdateAccessor;
import com.wandisco.gerrit.gitms.shared.api.repository.BatchGitUpdateRequest;
import com.wandisco.gerrit.gitms.shared.api.repository.BatchGitUpdateResult;
import com.wandisco.gerrit.gitms.shared.api.repository.GitUpdateAccessor;
import com.wandisco.gerrit.gitms.shared.api.repository.GitUpdateRequest;
import com.wandisco.gerrit.gitms.shared.api.repository.GitUpdateResult;
import com.wandisco.gerrit.gitms.shared.api.repository.RefLogInfo;
import com.wandisco.gerrit.gitms.shared.exception.ConfigurationException;
import com.wandisco.gerrit.gitms.shared.util.ObjectUtils;
import org.eclipse.jgit.errors.RepositoryAlreadyExistsException;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.GitConfiguration;
import org.eclipse.jgit.util.ReplicationConfiguration;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wandisco.gerrit.gitms.shared.api.repository.GitUpdateObjectsFactory.buildUpdateRequestWithPackfile;
import static org.eclipse.jgit.lib.Constants.REPLICATION_USE_GIT_UPDATE_SCRIPT;
import static org.eclipse.jgit.util.ReplicationConfiguration.getOverrideBehaviour;

/**
 * Replicated class which helps and executes the replication of Git Repository Update commands.
 */
public class ReplicatedUpdate {

    // Note: Production code should never flip this value to true, it is only for testing cgit -> jgit packfile generation
    private static final boolean useRPGitUpdateScript = getOverrideBehaviour(REPLICATION_USE_GIT_UPDATE_SCRIPT, false);

    private static final Logger logger = LoggerFactory.getLogger(ReplicatedUpdate.class);

    /**
     * Call into the replicated engine to perform this git update.
     * It will actually perform all the work, so we only really check the results on its return.
     *
     * @param user User account performing the update.
     * @param oldRevId Old revision ID.
     * @param newRevId New revision ID.
     * @param refName Ref to be updated.
     * @param repo Repository info for repository being updated.
     * @param refLog Entry to write to reflog.
     * @return RefUpdate result of the update operation.
     * @throws IOException If the update content could not be created, or the update could not be replicated.
     */
    public static RefUpdate.Result replicateUpdate(final String user,
                                                   final ObjectId oldRevId, final ObjectId newRevId,
                                                   final String refName, final Repository repo, final RefLogInfo refLog) throws
            IOException {
        try {
            GitUpdateRequest gitUpdateRequest;

            // As we now use objectid, the interface can receive nulls, but to make the coding logic easier - turn
            // any nulls into EMPTY_SHA now, to ensure it matches all expectations later and in the rp-update-script.
            final ObjectId oldRev =  oldRevId == null ? ObjectId.zeroId() : oldRevId;
            final ObjectId newRev = newRevId == null ? ObjectId.zeroId() : newRevId;

            // Take a decision here whether we are to use the rp-git-update script and have it do the packfile
            // generation for us - or use jgit to do this now. Default is to use jgit but we support the old route
            // for consistency checking easily.
            if (!useRPGitUpdateScript) {
                gitUpdateRequest = buildUpdateRequestWithPackfile(refName, oldRev, newRev, user, repo, refLog);
            } else {
                gitUpdateRequest = generatePackfilesRequiredForUpdate(refName, oldRev.getName(),
                        newRev.getName(), user, repo);
            }
            GitUpdateResult result = GitUpdateAccessor.updateRepository(gitUpdateRequest);

            if (result == null) {
                StringBuilder sb = new StringBuilder("Unable to find a GitUpdateResult to the update repository call: ");
                sb.append(gitUpdateRequest.toString());
                // its an error, but its additional information that we only want to record at debug log level.
                logger.debug("Error: GitUpdateFailure, {}", sb);
                throw new IOException(sb.toString());
            }

            refreshAndReloadDBIfNeeded(repo);

            return result.getUpdateResultCode();
        } catch (GitUpdateException | ConfigurationException e) {
            // its an error, but its additional information that we only want to record at debug log level.
            logger.debug("Exception happened when updating repo: {}", e.getMessage());
            // If we return null here it is not possible to work out what happened.
            // Due to the call chain we have to wrap as an IOException as it knows
            // nothing about GitUpdateException.
            throw new IOException(e);
        }
    }

    // In a replicated update git-ms may create or update refs. In the case our repo is using refTable, the
    // git-ms thread refreshed its cache. However, the gerrit cache may be stale. So refresh it here.
    private static void refreshAndReloadDBIfNeeded(Repository repo) throws IOException {
        // Need to refresh the database so that it is in the correct state prior to gerrit deciding what events to send
        // out.
        repo.getRefDatabase().refreshAndReload();
    }

    /**
     * Call into the replicated engine to get information about a GitUpdate and generate the relative packfile
     * for replication.
     *
     * @param user User account performing the update.
     * @param oldRev Old revision ID.
     * @param newRev New revision ID.
     * @param refName Ref to be updated.
     * @param repo Repository info for repository being updated.
     * @return GitUpdateRequest the update request object filled with appropriate information for the replication
     * request.
     * @throws IOException If the packfile cannot be written.
     */
    public static GitUpdateRequest generatePackfilesRequiredForUpdate(final String user,
                                                                      final String oldRev,
                                                                      final String newRev,
                                                                      final String refName,
                                                                      final Repository repo) throws
            IOException {

        if (repo == null) {
            throw new IOException("Invalid - null repository supplied.");
        }

        final String[] commandUpdate = {getRpUpdateScript(), "-r", refName, oldRev, newRev};
        final String fsPath = repo.getDirectory().getAbsolutePath();

        StringBuilder processOutput = new StringBuilder();
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_DIR", ".");
        if (!StringUtils.isEmptyOrNull(user)) {
            environment.put("ACP_USER", user);
        }

        // this call will block until the update has happened on the local node
        final int returnCode = execProcess(commandUpdate, new File(fsPath), environment, processOutput, null);

        if (returnCode != 0) {
            throw new IOException(processOutput.toString());
        }

        // otherwise the process should have returned the json information about the GitUpdate and the packfile it has
        // generated, so we can create a GitUpdate object with this information now.
        final String json = processOutput.toString();
        if (StringUtils.isEmptyOrNull(json)) {
            // invalid null json response information from the update script even though it indicated success?
            throw new IOException("Invalid null json return information from the rp-git-update packfile generation");
        }

        return ObjectUtils.createObjectFromJson(json, GitUpdateRequest.class);
    }

    /**
     * Entrypoint to do the replicated update of a batch request.
     *
     * @param user User account performing update.
     * @param updateRequestList List of individual updates to make as a batch.
     * @param repo Repository info for repository being updated.
     * @return BatchGitUpdateRequestResult a result with the update results of each item in the batch, and an overal
     * result member.
     * @throws IOException If the content for update cannot be created, or the update cannot be replicated.
     */
    public static BatchGitUpdateResult replicatedBatchUpdate(final String user,
                                                             final List<ReceiveCommand> updateRequestList,
                                                             final Repository repo)
            throws IOException {

        BatchGitUpdateRequest batchGitUpdateRequest = null;

        try {
            // This creates the packfile and returns a fully populated request item, in this case for a batch request.
            // if it was a single GitUpdate it would return same information in a GitUpdateRequest instead.
            // But we do not issue the request here - only have it built and packfile generated!.
            batchGitUpdateRequest = buildUpdateRequestWithPackfile(user, updateRequestList, repo);
        } catch (IOException e) {
            // Unable to get this items packfile generated.  Either a real IOException on disk, or
            // Something has failed validation.. Log details and throw.
            logger.debug("Failed to generate batch update request with packfile: {}.  Error Details: {}",
                    updateRequestList, e.getMessage());
            throw e;
        } catch (ConfigurationException e) {
            // Looks like a GitMS Configuration exception - we cannot go any further.
            logger.debug("Failed to generate batch update request with packfile: {}.  Error Details: {}",
                    updateRequestList, e.getMessage());
            throw new IOException("Failed to generate a batch git update request.", e);
        }

        // Perform the actual replicated update request now - over REST.
        try {
            BatchGitUpdateResult result = BatchGitUpdateAccessor.updateRepository(batchGitUpdateRequest);
            if (result == null) {
                StringBuilder sb = new StringBuilder("Unable to find a BatchGitUpdateResult for the update repository call: ");
                sb.append(batchGitUpdateRequest);
                // its an error, but its additional information that we only want to record at debug log level.
                logger.debug("Error: BatchGitUpdateFailure, {}", sb);
                throw new IOException(sb.toString());
            }

            refreshAndReloadDBIfNeeded(repo);

            return result;
        } catch (IOException e) {
            // rethrow raw.
            throw e;
        } catch (Exception e) {
            // Record any update failure as an IOException also - as that is all JGit native was aware of and it prevents
            // a large update chain of checked exception handling cases.
            throw new IOException(e);
        }
    }

    /**
     * replicatedCreate allows for the creation of a repository on all replicated nodes.  this not only
     * creates the repo but also adds it to the list of governed repos by GitMS.
     *
     * @param absolutePath Full path to new repository.
     * @throws IOException If the repository cannot be created at given location.
     */
    public static void replicatedCreate(String absolutePath) throws IOException {
        final String port = ReplicationConfiguration.getPort();
        final String timeout = ReplicationConfiguration.getRepoDeployTimeout();

        if (StringUtils.isEmptyOrNull(port)) {
            throw new IOException("Invalid Replication Setup - no replication port currently configured.");
        }

        if (StringUtils.isEmptyOrNull(timeout)) {
            throw new IOException("Invalid Replication Setup - no replication port currently configured.");
        }

        try {
            // TODO: GER-1394 Move this create command into the shared library beside updaterepository REST call.
            // Keep this logic shared and out of jgit. Should be same as call:
            // BatchGitUpdateResult result = BatchGitUpdateAccessor.updateRepository(batchGitUpdateRequest);

            final String repoPath = URLEncoder.encode(absolutePath, "UTF-8");
            URL url = new URL("http://127.0.0.1:" + port + "/gerrit/deploy?"
                    + "timeout=" + timeout + "&repoPath=" + repoPath);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setUseCaches(false);
            httpCon.setRequestMethod("PUT");
            httpCon.setRequestProperty("Content-Type", "application/xml");
            httpCon.setRequestProperty("Accept", "application/xml");
            int response = httpCon.getResponseCode();

            //an error may have happened, and if it did, the errorstream will be available
            //to get more details - but if repo deployment was successful, getErrorStream
            //will be null
            StringBuilder responseString = new StringBuilder();
            if (httpCon.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpCon.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseString.append(line);
                        responseString.append(System.lineSeparator());
                    }
                }
            }

            httpCon.disconnect();

            final String failureMsg = "Failure to create the git repository on the GitMS Replicator, " +
                    "response code: %s, Replicator response: %s";

            if (response == 412) {
                // there has been a problem with the deployment
                throw new RepositoryAlreadyExistsException(String.format(failureMsg, response, responseString));
            }

            if (response != 200) {
                //there has been a problem with the deployment
                throw new IOException(String.format(failureMsg, response, responseString));
            }

        } catch (RepositoryAlreadyExistsException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IOException("Error deploying repo: " + ex.getMessage());
        }
    }

    /**
     * Determine the replicated update script to use. The script name can be
     * defined in the git config.
     * <p>
     * The git config file to be used can defined using the GIT_CONFIG
     * environment
     * variable, if this is not set the current user's .gitconfig file is used.
     *
     * @return The name of the update script including path if it exists in the
     * git config, the default 'rp-git-update' which should exist on the
     * path otherwise.
     * @throws IOException git config could not be read or is incorrect format.
     */
    private static String getRpUpdateScript() throws IOException {
        return GitConfiguration.getAndCheckGitHook("core", null, "rpupdatehook", "rp-git-update");
    }


    /**
     * As per https://jira.wandisco.com/browse/GER-49
     * Executes a process described in command[], in the workingDir, adding
     * the envVars variables to the environment.
     * The output (stdout and stderr) of the process will be available in the
     * processOutput StringBuilder which must be provided
     * by the caller. The stderror will be redirected to the stdoutput
     *
     * @param command       The command to be executed
     * @param workingDir    The working dir
     * @param envVars       The environment variables
     * @param processOutput a StringBuilder provided by the caller which will
     *                      contain the process output (stderr and stdout)
     * @param processStdIn  a String provided by the caller with the stdin info.
     * @return The return code of the process
     * @throws IOException If an error occurred during the sub-process execution or the output could not be read.
     */
    @SuppressWarnings("EmptyCatch")
    private static int execProcess(String[] command, final File workingDir, Map<String, String> envVars,
                                   final StringBuilder processOutput,
                                   String processStdIn) throws IOException {
        ProcessBuilder proBuilder = new ProcessBuilder(command);
        proBuilder.redirectErrorStream(true);
        proBuilder.directory(workingDir);
        Map<String, String> environment = proBuilder.environment();
        environment.putAll(envVars);

        logger.debug("Running command={} , in={}, input={}", command[0], workingDir, processStdIn);
        final Process process = proBuilder.start();

        BufferedReader br =
                new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
        if (processStdIn != null) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(process.getOutputStream()));
            pw.println(processStdIn);
            pw.close();
        }
        String line;
        String newLine = System.getProperty("line.separator");
        processOutput.append(newLine);
        try {
            while ((line = br.readLine()) != null) {
                processOutput.append(line).append(newLine);
            }
        } catch (IOException e) {
            processOutput.append("\"Failure to replicate update.\"[Caught IOException: ").append(e.getMessage());
            int processReturnCode = 0;
            try {
                processReturnCode = process.waitFor();
            } catch (InterruptedException ignored) {
            }
            processOutput.append(", return code=").append(processReturnCode).append("]");
            throw new IOException(processOutput.toString(), e);
        }

        int returnCode = -1;
        try {
            returnCode = process.waitFor();
        } catch (InterruptedException ex) {
        }

        return returnCode;
    }
}
