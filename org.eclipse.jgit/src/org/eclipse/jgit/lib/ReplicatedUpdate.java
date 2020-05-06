package org.eclipse.jgit.lib;

import com.wandisco.gerrit.gitms.shared.api.exceptions.GitUpdateException;
import com.wandisco.gerrit.gitms.shared.api.repository.*;
import com.wandisco.gerrit.gitms.shared.util.ObjectUtils;
import com.wandisco.gerrit.gitms.shared.util.PackUtils;
import org.eclipse.jgit.util.GitConfiguration;
import org.eclipse.jgit.util.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.jgit.lib.Constants.REPLICATION_REFUPDATE_LOGGING;
import static org.eclipse.jgit.lib.Constants.REPLICATION_USE_GIT_UPDATE_SCRIPT;
import static org.eclipse.jgit.util.ReplicationConfiguration.getOverrideBehaviour;

/**
 * Replicated class which helps and executes the replication of Git Repository Update commands.
 */
public class ReplicatedUpdate {

    // HACKY logging property looks like it was only set in code before - now can be setup in java prop / env,
    // N.B. ONLY for dev use
    private static final boolean logMeEnabled = getOverrideBehaviour(REPLICATION_REFUPDATE_LOGGING);
    // TODO: trevorg Flip this default back to false, when we get the inProc code written! GER-977
    private static final boolean useRPGitUpdateScript = getOverrideBehaviour(REPLICATION_USE_GIT_UPDATE_SCRIPT, true);


    /**
     * Call into the replicated engine to perform this git update.
     * It will actually perform all the work, so we only really check the results on its return.
     *
     * @param user
     * @param fsPath
     * @param oldRevId
     * @param newRevId
     * @param refName
     * @param repo
     * @return RefUpdate result of the update operation.
     * @throws IOException
     */
    public static RefUpdate.Result executeReplicatedUpdate(final String user, final String fsPath,
                                                           final ObjectId oldRevId, final ObjectId newRevId,
                                                           final String refName, final Repository repo) throws
            IOException {
        if (!useRPGitUpdateScript) {
            // use the new in process gitupdate, which does the verification here using jgit, and makes
            // the update call via http directly.
            GitUpdateRequest gitUpdateRequest =
                    PackUtils
                            .executePackfileGenerationBeforeReplicationUpdateInProcess(refName, oldRevId, newRevId,
                                                                                       user,
                                                                                       fsPath, repo);
            try {
                GitUpdateResult result = GitUpdateAccessor.updateRepository(gitUpdateRequest);

                if (result == null) {
                    logMe("Unable to find a GitUpdateResult to the update repository call: " +
                          gitUpdateRequest.toString());
                    return null;
                }

                return result.updateResultCode;
            } catch (GitUpdateException e) {
                logMe("Exception happening whem updating repo: ", e);

                // Return null, and let the outside code try to work out the failure as we couldn't even cast the
                // return information to a GitUpdateResult of some type.
                return null;
            }
        }

        // This is the fallback mechanism we used to use which is to call the update script to do our replication
        // work / creation of packfile / verification and replication call.
        final String oldRev = ObjectId.toString(oldRevId);
        final String newRev = ObjectId.toString(newRevId);
        executeReplicatedUpdateOutOfProcess(user, fsPath, oldRev, newRev, refName);
        return null;
    }

    /**
     * Exeutes the replicated update, via the rp-git-update script out of the main java process ( jgit ) by a remote
     * exec process call.
     *
     * @param user
     * @param fsPath
     * @param oldRev
     * @param newRev
     * @param refName
     * @throws IOException
     */
    private static void executeReplicatedUpdateOutOfProcess(final String user, final String fsPath,
                                                            final String oldRev, final String newRev,
                                                            final String refName) throws IOException {
        String[] commandUpdate = {getRpUpdateScript(), refName, oldRev, newRev};

        StringBuilder processOutput = new StringBuilder();
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_DIR", ".");
        if (!StringUtils.isEmptyOrNull(user)) {
            environment.put("ACP_USER", user);
        }

        // this call will block until the update has happened on the local node
        int returnCode = execProcess(commandUpdate, new File(fsPath), environment, processOutput, null);

        if (returnCode != 0) {
            // only prepend failure to replicate if we have a failure!!
            throw new IOException("Failure to replicate: " + processOutput.toString());
        }
    }

    /**
     * Call into the replicated engine to get information about a GitUpdate and generate the relative packfile
     * for replication.
     *
     * @param user
     * @param fsPath
     * @param oldRev
     * @param newRev
     * @param refName
     * @return GitUpdateRequest the update request object filled with appropriate information for the replication
     * request.
     * @throws IOException
     */
    public static GitUpdateRequest executePackfileGenerationBeforeReplicatingUpdate(final String user,
                                                                                    final String fsPath,
                                                                                    final String oldRev,
                                                                                    final String newRev,
                                                                                    final String refName) throws
            IOException {
        String[] commandUpdate = {getRpUpdateScript(), "-r", refName, oldRev, newRev};

        StringBuilder processOutput = new StringBuilder();
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_DIR", ".");
        if (!StringUtils.isEmptyOrNull(user)) {
            environment.put("ACP_USER", user);
        }

        // this call will block until the update has happened on the local node
        int returnCode = execProcess(commandUpdate, new File(fsPath), environment, processOutput, null);

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

        GitUpdateRequest result = ObjectUtils.createObjectFromJson(json, GitUpdateRequest.class);
        return result;
    }

    /**
     * Entrypoint to do the replicated update of a batch request.
     *
     * @param user
     * @param fsPath
     * @param updateRequestList
     * @param repo
     * @return BatchGitUpdateRequestResult a result with the update results of each item in the batch, and an overal
     * result member.
     * @throws IOException
     */
    public static BatchGitUpdateResult executeBatchReplicatedUpdate(final String user,
                                                                    final String fsPath,
                                                                    final BatchGitUpdateRequestsList updateRequestList,
                                                                    final Repository repo)
            throws IOException {

        BatchGitUpdateRequestsList updateRequestsWithPackfiles = new BatchGitUpdateRequestsList();

        // Get me a real GitUpdateRequest with the real packfile generated for each item here
        for (GitUpdateRequest singleUpdate : updateRequestList) {
            // Get an updated request object.... This will contain the packfile object name generated and the final
            // GitUpdateRequest to be issued for this command.
            try {
                GitUpdateRequest gitUpdateRequest;

                if (!useRPGitUpdateScript) {
                    // use the new in process gitupdate, which does the verification here using jgit, and makes
                    // the update verification inprocess along with packfile generation.
                    gitUpdateRequest =
                            PackUtils
                                    .executePackfileGenerationBeforeReplicationUpdateInProcess(singleUpdate.getRefName(),
                                                                                               ObjectId.fromString(singleUpdate.getOldRev()),
                                                                                               ObjectId.fromString(singleUpdate.getNewRev()),
                                                                                               singleUpdate.getUserid(),
                                                                                               singleUpdate.getGitDir(),
                                                                                               repo);
                }
                else {
                    gitUpdateRequest = executePackfileGenerationBeforeReplicatingUpdate(singleUpdate.getUserid(),
                                                                                        singleUpdate.getGitDir(),
                                                                                        singleUpdate.getOldRev(),
                                                                                        singleUpdate.getNewRev(),
                                                                                        singleUpdate.getRefName());
                }

                // now add result to our batch...
                updateRequestsWithPackfiles.add(gitUpdateRequest);
            } catch (IOException e) {
                // Unable to get this items packfile generated.  Either a real IOException on disk, or
                // Something has failed validation.. Log details and throw.
                logMe(String.format("Failed to generate part of a batch update request: %s.  Error Details: %s",
                                    singleUpdate.toString(),
                                    e.getMessage()));
                throw e;
            }
        }

        // Create the final BatchRequest and issue it to the GitMS Delegate.
        BatchGitUpdateRequest batchGitUpdateRequest = new BatchGitUpdateRequest(fsPath, user,
                                                                                updateRequestsWithPackfiles);

        try {
            BatchGitUpdateResult result = BatchGitUpdateAccessor.updateRepository(batchGitUpdateRequest);
            if (result == null) {
                logMe("Unable to find a BatchGitUpdateResult to the update repository call: " +
                      batchGitUpdateRequest.toString());
                return null;
            }
            return result;
        } catch (Exception e) {
            // update repository return an IOException instead of a GitUpdateResult....
            throw new IOException(e);
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
     * @throws IOException
     */
    private static int execProcess(String[] command, final File workingDir, Map<String, String> envVars,
                                   final StringBuilder processOutput,
                                   String processStdIn) throws IOException {
        ProcessBuilder proBuilder = new ProcessBuilder(command);
        proBuilder.redirectErrorStream(true);
        proBuilder.directory(workingDir);
        Map<String, String> environment = proBuilder.environment();
        environment.putAll(envVars);

        logMe("Running " + command[0] + ", in " + workingDir + ", input=" + processStdIn);
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


    /**
     * log utility to be used for debug purposes
     *
     * @param s String to be logged.
     */
    public static void logMe(String s) {
        if (!logMeEnabled) {
            return;
        }
        try (PrintWriter p = new PrintWriter(new FileWriter("/tmp/gitms.log", true))) {
            p.println(new Date().toString());
            p.println(s);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }

    /**
     * log utility to be used for debug purposes
     *
     * @param s String to be logged.
     * @param e Exception to be logged.
     */
    public static void logMe(String s, Throwable e) {
        if (!logMeEnabled) {
            return;
        }
        try (PrintWriter p = new PrintWriter(new FileWriter("/tmp/gitms.log", true))) {
            p.println(new Date().toString());
            p.println(s);
            if (e != null) {
                p.println(e);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }

}
