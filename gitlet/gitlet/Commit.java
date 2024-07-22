package gitlet;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.time.*;

import static gitlet.Utils.*;

/**
 *  Represents a gitlet commit object.
 *  does at a high level.
 *
 *  @author AGX
 */
public class Commit implements Serializable {
    /**
     *
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private String message;
    private Instant timestamp;

    private String hash;

    /* blobs in the commit */
    Map<String, String> blobs; /* (name of the files, Hash of the Commit's blobs)
    and hashes stored in when invoking the add command for example
    we'll see if the hash in the blob in our map is the same as the one
    we're computing for the files we want to add
    the state of the files we're pointing at */


    /** Parent of this commit */
    String parentHash;

    File location;

    String secondParentHash;

    boolean merged;

    String mergedIntoBranch = "";


    public Commit(Commit parent, Map<String, File> filesToBeAdded, String message, Instant timestamp,
                  Set<String> filesToRemove) {
        this.parentHash = parent.getHash();
        this.message = message;
        this.timestamp = timestamp;
        blobs = new HashMap<>();
        adjustBlobs(filesToBeAdded, filesToRemove);
        // Each commit is identified by its SHA-1 id, which must include the file (blob)
        // references of its files, parent reference, log message, and commit time.
        hash = sha1(this.blobs.toString(), parentHash, message, timestamp.toString());
        saveCommit(hash);
        merged = false;
    }




    public void printCommit() {
        System.out.printf("===%n");
        System.out.printf("commit %s%n", this.getHash());
        if (merged) {
            System.out.printf("Merge: %s %s%n", parentHash.substring(0, 8), secondParentHash.substring(0, 8));
        }
        System.out.printf("Date: %s%n", this.getFormattedTimestamp());
        System.out.printf(this.getMessage());
        System.out.println();
    }

    public boolean checkIfFileIsPresent(File file) {
        return blobs.containsKey(file.getName());
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Commit getParent() {
        return getParent(parentHash);
    }

    public Commit getParent(String Hash) {
        if (Hash == null) {
            return null;
        }
        return fromFile(Hash);
    }

    public Commit getSecondParent() {
        return getParent(secondParentHash);
    }

    public String getMessage() {
        return message;
    }

    public static Commit fromFile(String hash) {
        if (hash == null) {
            return null;
        }
        File commitFile = join(Repository.COMMIT_DIR, hash);
        if (!commitFile.exists()) {
            return null;
        }
        return readObject(commitFile, Commit.class);
    }

    private void saveCommit(String Hash) {

        File commitFile = Utils.join(Repository.COMMIT_DIR, Hash);

        Repository.COMMIT_DIR.mkdirs();

        try {
            if (!commitFile.exists()) {
                commitFile.createNewFile();
            }
        } catch (IOException e) {
            System.out.println("couldn't create Commit file"); // for debugging
            System.out.println(e.getMessage());
            e.printStackTrace(); // Print stack trace for detailed error analysis
            return;
        }

        writeObject(commitFile, this);

        location = commitFile;
    }

    private void adjustBlobs(Map<String, File> filesToBeAdded, Set<String> filesToRemove) {
        Commit parent = fromFile(parentHash);
        Map<String, String> parentBlobs = parent.blobs;
        for (Map.Entry<String, String> entry : parentBlobs.entrySet()) {
            if (filesToRemove.contains(entry.getKey())) {
                continue;
            }
            if (filesToBeAdded.containsKey(entry.getKey())) {
                this.blobs.put(entry.getKey(), (new Blob(filesToBeAdded.get(entry.getKey()))).getHash());
            } else {
                this.blobs.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, File> entry : filesToBeAdded.entrySet()) {
            if (!this.blobs.containsKey(entry.getKey())) {
                this.blobs.put(entry.getKey(), (new Blob(filesToBeAdded.get(entry.getKey()))).getHash());
            }
        }
    }


    public Commit(String message, Instant timestamp) {
        this.message = message;
        this.timestamp = timestamp;
        parentHash = null;
        secondParentHash = null;
        blobs = new HashMap<>();
        this.hash = sha1(message, timestamp.toString());
        saveCommit(hash);
    }

    public Commit(Commit firstParent, Commit secondParent, Commit splitPoint, String currentBranch,
                  String mergedIntoBranch, Instant timestamp, Repository repo) {
        this.parentHash = firstParent.getHash();
        this.secondParentHash = secondParent.getHash();
        this.timestamp = timestamp;
        blobs = new HashMap<>();
        this.message = String.format("Merged %s into %s.", mergedIntoBranch, currentBranch);
        if (merge(splitPoint, firstParent, secondParent, repo)) {
            System.out.println("Encountered a merge conflict.");
        }
        hash = sha1(this.blobs.toString(), parentHash, secondParentHash, message, timestamp.toString());
        saveCommit(hash);
        merged = true;
        this.mergedIntoBranch = mergedIntoBranch;
    }

    private boolean merge(Commit splitPoint, Commit HEAD, Commit branch, Repository repo) {
        Map<String, String> filesInCurrentBranch = HEAD.blobs;
        Map<String, String> filesInOtherBranch = branch.blobs;
        boolean confilctDetected = false;

        for (Map.Entry<String, String> entry : splitPoint.blobs.entrySet()) {
            String fileName = entry.getKey();
            String splitHash = entry.getValue();
            String currentHash = filesInCurrentBranch.get(fileName);
            String otherHash = filesInOtherBranch.get(fileName);
            // file is present in both branches
            if (currentHash != null  && otherHash != null) {
                // files are not the same
                if (!currentHash.equals(otherHash) && currentHash.equals(splitHash)) {
                    // file is unchanged in current branch
                    this.blobs.put(entry.getKey(), otherHash);
                    writeToFileFromBlob(fileName, Blob.fromFile(otherHash));
                } else if (!currentHash.equals(otherHash) && otherHash.equals(splitHash)) {
                    // file is unchanged in other branch
                    this.blobs.put(entry.getKey(), currentHash);
                } else if (!currentHash.equals(otherHash) && !otherHash.equals(splitHash) &&
                        !currentHash.equals(splitHash)) {
                    // file is modified in both
                    // conflict
                    File conflict = conflictFile(entry.getKey(), Blob.fromFile(currentHash),
                            Blob.fromFile(otherHash));
                    blobs.put(entry.getKey(), (new Blob(conflict)).getHash());
                    // stageFile(conflict);
                    confilctDetected = true;
                } else if (currentHash.equals(otherHash) && otherHash.equals(splitHash)) {
                    blobs.put(entry.getKey(), otherHash);
                } else if (currentHash.equals(otherHash)) {
                    blobs.put(entry.getKey(), otherHash);
                }
            } else if (currentHash != null && otherHash == null) {
                if (currentHash.equals(splitHash)) {
                    restrictedDelete(join(Repository.CWD, fileName)); // delete it
                } else {
                    // conflict : modified in HEAD but not present in other Branch
                    File conflict = conflictFile(entry.getKey(),
                            Blob.fromFile(currentHash),
                            Blob.fromFile(otherHash));
                    blobs.put(fileName, (new Blob(conflict)).getHash());
                    confilctDetected = true;
                }
            } else if (otherHash != null && currentHash == null) {
                if (!otherHash.equals(splitHash)) {
                    File conflict = conflictFile(entry.getKey(),
                            Blob.fromFile(currentHash), Blob.fromFile(otherHash));
                    blobs.put(entry.getKey(), (new Blob(conflict)).getHash());

                    confilctDetected = true;
                }
            }



        }


        for (Map.Entry<String, String> entry : filesInCurrentBranch.entrySet()) {
            // not in split nor in other branch
            if (!splitPoint.blobs.containsKey(entry.getKey()) && !filesInOtherBranch.containsKey(entry.getKey())) {
                blobs.put(entry.getKey(), entry.getValue());
            }
        }



        for (Map.Entry<String, String> entry : filesInOtherBranch.entrySet()) {
            // not in split nor in HEAD
            if (!splitPoint.blobs.containsKey(entry.getKey()) && !filesInCurrentBranch.containsKey(entry.getKey())) {
                blobs.put(entry.getKey(), entry.getValue());
                writeToFileFromBlob(entry.getKey(), Blob.fromFile(filesInOtherBranch.get(entry.getKey())));
            } else if (filesInCurrentBranch.containsKey(entry.getKey())
                    && !splitPoint.blobs.containsKey(entry.getKey())) {
                // file is present in current branch and other branch but not in split point
                if (filesInCurrentBranch.get(entry.getKey()).equals(entry.getValue())) {
                    blobs.put(entry.getKey(), entry.getValue());
                } else {
                    // conflict
                    File conflict = conflictFile(entry.getKey(),
                            Blob.fromFile(filesInCurrentBranch.get(entry.getKey())),
                            Blob.fromFile(filesInOtherBranch.get(entry.getKey())));
                    blobs.put(entry.getKey(), (new Blob(conflict)).getHash());
                    confilctDetected = true;
                }
            }
        }
        return confilctDetected;
    }


    private File conflictFile(String fileName, Blob currBlobObj, Blob givenBlobObj) {
        File conflictFile = join(Repository.CWD, fileName);
        try {
            if (!conflictFile.exists()) {
                conflictFile.createNewFile();
            }
        } catch (IOException e) {
            System.out.println("couldn't create file");
            System.exit(0);
        }

        String currContent = "";
        if (currBlobObj != null) {
            currContent = currBlobObj.getContent();
        }

        String givenContent = "";
        if (givenBlobObj != null) {
            givenContent = givenBlobObj.getContent();
        }



        String content = "<<<<<<< HEAD\n" + currContent + "=======\n" + givenContent + ">>>>>>>\n";
        writeContents(conflictFile, content);


        return conflictFile;
    }

    public String getHash() {
        return hash;
    }

    private void writeToFileFromBlob(String fileName, Blob blob) {
        File file = join(Repository.CWD, fileName);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            writeContents(file, blob.getContent());
        } catch (IOException e) {
            System.out.println("couldn't create file");
            System.exit(0);
        }
    }



    // made with chatgpt to get formatted timestamp
    public String getFormattedTimestamp() {
        // Get the system default time zone
        ZoneId zoneId = ZoneId.systemDefault();

        // Convert Instant to ZonedDateTime with the system default time zone
        ZonedDateTime zonedDateTime = timestamp.atZone(zoneId);

        // Format ZonedDateTime to the desired string format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E MMM d HH:mm:ss yyyy Z");
        String formattedDateTime = zonedDateTime.format(formatter);


        return formattedDateTime;
    }

}
