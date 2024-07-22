package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

import static gitlet.Utils.*;


/**
 * Represents a gitlet repository.
 *  does at a high level.
 *
 *  @author AGX
 */
public class Repository implements Serializable {
    /**
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** The Commit directory. */
    public static final File COMMIT_DIR = join(GITLET_DIR, "commits");

    /** The .index directory. "The Staging Area" */
    public static final File INDEX_DIR = join(GITLET_DIR, ".index");

    /** the blob directory */
    public static final File BLOB_DIR = join(GITLET_DIR, "objects");


    /** the Repo directory */
    public static final File REPO_DIR = join(GITLET_DIR, "REPO");

/** the Repo File directory */
    public static final File REPO = join(REPO_DIR, "REPO");

    /** the pointer to the master */
    Commit master;

    /** the pointer to the current commit we're at "Head" */
    String HEADHash; // the hash of the HEAD Commit


    /** the pointer to the Current Branch we're in */
    String currentBranch;

    /** */
    Map<String,String> branches = new HashMap<>(); // (name of the branch, Hash of the last Commit of the Branch);

    // staged removed files
    Set<String> FilesToRemove = new HashSet<>();


    static boolean doesGitletDirExists() {
        return GITLET_DIR.exists() && GITLET_DIR.isDirectory();
    }

    public void init() {
        // create .gitlet subdirectory
        GITLET_DIR.mkdir();
        assert GITLET_DIR.exists() && GITLET_DIR.isDirectory();
        Commit HEAD = new Commit("initial commit", Instant.EPOCH);
        HEADHash = HEAD.getHash();
        branches.put("master", HEAD.getHash());
        currentBranch = "master";
        // Setup Persistence
        COMMIT_DIR.mkdir();
        assert COMMIT_DIR.exists() && COMMIT_DIR.isDirectory();
        BLOB_DIR.mkdir();
        assert BLOB_DIR.exists() && BLOB_DIR.isDirectory();
        INDEX_DIR.mkdir();
        assert INDEX_DIR.exists() && INDEX_DIR.isDirectory();
        REPO_DIR.mkdir();
        assert REPO_DIR.exists() && REPO_DIR.isDirectory();
        save();
    }

    public void save() {
        try {
            if (!REPO.exists()) {
                REPO.createNewFile();
            }
        } catch (IOException e) {
            System.out.println("Couldn't Create Repo File");
        }

        writeObject(REPO,this);
    }

    public static Repository fromFile() {
        if (!REPO.exists()) {
            return null;
        }
        return readObject(REPO, Repository.class);
    }

    /**
     * Staging an already-staged file overwrites the previous entry in the staging area with the new contents.
     * If the current working version of the file is identical to the version in the current commit, do not stage it to be added
     * @param fileName name of file to be added
     */
    public void add(String fileName) {
        FilesToRemove.remove(fileName);

        File file = join(CWD, fileName);

        Commit HEAD = Commit.fromFile(HEADHash);
        Map<String, String> filesInCurrCommit = HEAD.blobs;
        if (!file.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }


        if (file.isFile()) {
            File fileIndexLoc = join(INDEX_DIR, file.getName());
            if (filesInCurrCommit.containsKey(file.getName())) {
                String blobHash = filesInCurrCommit.get(file.getName());
                String hashOfFile = hashFile(file);

                /*
                 * compare the hash in the blob with the generated hash from the file at the directory
                 * If the current working version of the file is identical to the version in the current commit,
                 * do not stage it to be added
                 * */
                if (hashOfFile.equals(blobHash)) {
                    // remove it from the staging area if it is already there
                    if (fileIndexLoc.exists()) {
                        fileIndexLoc.delete();
                    }
                    return;
                }
            }

            // Create or overwrite the staged file
            try {
                if (!fileIndexLoc.exists()) {
                    fileIndexLoc.createNewFile();
                }

            } catch (IOException e) {
                System.out.println("couldn't create new File");
            }
            // make the file in the index
            String contentOfAddedFile = readContentsAsString(file);
            writeContents(fileIndexLoc, contentOfAddedFile);

        }
    }

    private String hashFile(File file) {
        String content = readContentsAsString(file);
        return sha1(content);
    }


    public Map<String, File> getFilesInIndex() {
        Map<String, File> filesToBeAdded = new HashMap();
        List<String> fileNames = plainFilenamesIn(INDEX_DIR);
        if (fileNames == null) {
            return null;
        }
        for (String nameOfFile : fileNames) {
            filesToBeAdded.put(nameOfFile, join(INDEX_DIR, nameOfFile));
        }
        return filesToBeAdded;
    }
    public void commit(String message) {
        Map<String, File> filesToBeAdded = getFilesInIndex();
        if (filesToBeAdded == null) {
            System.exit(0);
        }

        if (filesToBeAdded.isEmpty() && FilesToRemove.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }

        Commit HEAD = Commit.fromFile(HEADHash);
        HEAD = new Commit(HEAD, filesToBeAdded, message,Instant.now(), FilesToRemove);

        FilesToRemove = new HashSet<>();

        this.HEADHash = HEAD.getHash();
        branches.put(currentBranch, HEADHash);

        // delete the files in the index
        for (File file : filesToBeAdded.values()) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public void rm(String fileName) {
        File fileToRemove = join(CWD, fileName);
        if (!fileToRemove.exists()) {
            FilesToRemove.add(fileName);
            return;
        }
        String fileToRemoveHash = hashFile(fileToRemove);
        // Unstage the file if it is currently staged for addition.
        List<String> stagedFiles = plainFilenamesIn(INDEX_DIR);

        if (stagedFiles != null && stagedFiles.contains(fileToRemove.getName())) {
            String fileInIndexHash = hashFile(join(INDEX_DIR, fileName));
            if (fileInIndexHash.equals(fileToRemoveHash)) {
                // remove file from the index.
                (join(INDEX_DIR, fileName)).delete();
                return;
            }
        }
        Commit HEAD = Commit.fromFile(HEADHash);
        if (!HEAD.blobs.containsKey(fileName)) {
            // do not remove it unless it is tracked in the current commit
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
        // stage it for removal
        FilesToRemove.add(fileName);

        if (fileToRemove.exists()) {
            Utils.restrictedDelete(fileToRemove);
        }

    }

    public void log() {
        Commit HEAD = Commit.fromFile(HEADHash);
        Commit current = HEAD;

        while (current != null) {
            current.printCommit();
            System.out.println();
            current = current.getParent();
        }
    }

    public void global_log() {
        List<String> commitFiles = Utils.plainFilenamesIn(COMMIT_DIR);
        if (commitFiles == null) {
            System.exit(0);
        }
        for (String nameOfFile : commitFiles) {
//            Commit.fromFile(nameOfFile); // Because the name of the File is the hash
            // another Approach
            Commit currCommit = Utils.readObject(join(COMMIT_DIR, nameOfFile), Commit.class);
            currCommit.printCommit();
            System.out.println();
        }
    }

    public void find(String message) {
        List<String> commitFiles = Utils.plainFilenamesIn(COMMIT_DIR);
        if (commitFiles == null) {
            System.out.println("Found no commit with that message.");
            System.exit(0);
        }
        List<String> res = new LinkedList<>();

        for (String nameOfFile : commitFiles) {
            Commit currCommit = Utils.readObject(join(COMMIT_DIR, nameOfFile), Commit.class);
            String messageOfCommit = currCommit.getMessage();
            if (message.equals(messageOfCommit)) {
                res.add(currCommit.getHash());
            }
        }

        for (String id : res) {
            System.out.println(id);
        }
        if (res.isEmpty()) {
            System.out.println("Found no commit with that message.");
        }
    }



    public void status() {
        System.out.println("=== Branches ===");
        List<String> branchNames = new ArrayList<>(branches.keySet());
        Collections.sort(branchNames);
        for (String branch : branchNames) {
            if (branch.equals(currentBranch)) {
                System.out.printf("*%s%n", currentBranch);
                continue;
            }
            System.out.println(branch);

        }
        System.out.println();

        Map<String, File> filesToBeAdded = getFilesInIndex();

        List<String> addedFiles = new ArrayList<>(filesToBeAdded.keySet());
        Collections.sort(addedFiles);

        System.out.println("=== Staged Files ===");
        for (String fileName : addedFiles) {
            System.out.println(fileName);
        }
        System.out.println();


        System.out.println("=== Removed Files ===");
        List<String> filesToRemove = new ArrayList<>(FilesToRemove);
        Collections.sort(filesToRemove);
        for (String removed : filesToRemove) {
            System.out.println(removed);
        }
        System.out.println();


        List<String> filesInWD = plainFilenamesIn(CWD);
        Commit HEAD = Commit.fromFile(HEADHash);

        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String fileName: filesInWD) {
            File file = join(CWD, fileName);
            if (HEAD.blobs.containsKey(file.getName()) && !compareHashesInCommitAndWD(file, HEAD) && !filesToBeAdded.containsKey(fileName)) {
                System.out.println(fileName + " (modified)");
            }
        }
        for (String fileName: HEAD.blobs.keySet()) {
            File file = join(CWD, fileName);
            if(!filesInWD.contains(fileName) && !filesToRemove.contains(fileName)) {
                System.out.println(fileName + " (deleted)");
            }
        }
        System.out.println();


        System.out.println("=== Untracked Files ===");
        if (filesInWD != null) {
            Collections.sort(filesInWD);
            for (String fileName : filesInWD) {
                File file = join(CWD, fileName);
                if (file.isFile() && !filesToBeAdded.containsKey(file.getName()) && HEAD != null &&!HEAD.blobs.containsKey(file.getName())) {
                    System.out.println(file.getName());
                }
            }
        }
        System.out.println();

    }

    /**
     * compare the file in the Head commit if it exists and the file
     * if true : files are the same
     * false : they are not
     * @param file
     * @param HEAD
     * @return
     */
    public boolean compareHashesInCommitAndWD(File file ,Commit HEAD) {
        Map<String, String> filesInCurrCommit = HEAD.blobs;
        if (!filesInCurrCommit.containsKey(file.getName())) {
            return false;
        }
        String blobHash = filesInCurrCommit.get(file.getName());
        String hashOfFile = hashFile(file);

        if (hashOfFile.equals(blobHash)) {
            return true;
        }
        return false;
    }

    /**
     * the first usage of checkout command out of three
     * usage : java gitlet.Main checkout -- [file name]
     * Takes the version of the file as it exists in the head commit and puts it in the working directory,
     * overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param fileName name of the file to get from HEAD
     */
    public void checkout(String fileName) {

        Commit HEAD = Commit.fromFile(HEADHash);

        createOrChangeFileInWD(fileName, HEAD);
    }

    /**
     * Takes the version of the file as it exists in the commit with the given id, and puts it in the working directory,
     * overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param fileName name of the file to get from commit
     * @param commitId hash of the commit
     */
    public void checkout(String fileName, String commitId) {
        Commit commit = null;
        if (commitId.length() < 40) {
            commit = Commit.fromFile(findCommitStartsWith(commitId));
        } else {
            commit = Commit.fromFile(commitId);
        }

        if (commit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        assert commit != null;
        createOrChangeFileInWD(fileName, commit);
    }

    private String findCommitStartsWith(String commitId) {
        List<String> Commits = plainFilenamesIn(COMMIT_DIR);
        if (Commits == null) {
            return null;
        }

        for (String id : Commits) {
            if (id.startsWith(commitId)) {
                return id;
            }
        }
        return  null;
    }

    private void createOrChangeFileInWD(String fileName, Commit commit) {
        // If the file does not exist in the previous commit, abort, printing the error message
        // File does not exist in that commit. Do not change the CWD.

        if (!commit.blobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        Blob fileBlob = Blob.fromFile(commit.blobs.get(fileName));

        assert fileBlob != null;

        File fileInWD = join(CWD, fileName);
        if (!fileInWD.exists()) {
            try {
                fileInWD.createNewFile();
            } catch (IOException e) {
                System.out.println("couldn't create file in checkout command");
            }
        }
        Utils.writeContents(fileInWD, fileBlob.getContent());
    }

    /**
     * Takes all files in the commit at the head of the given branch, and puts them in the working directory,
     * overwriting the versions of the files that are already there if they exist. Also, at the end of this command,
     * the given branch will now be considered the current branch (HEAD). Any files that are tracked in
     * the current branch but are not present in the checked-out branch are deleted.
     * The staging area is cleared, unless the checked-out branch is the current branch
     * @param fileName_branchName : might be file name or a branch name
     * @param isFileName : if isFileName is true then we use the first usage
     * else we use the 3rd option "java gitlet.Main checkout [branch name]"
     */
    public void checkout(String fileName_branchName, boolean isFileName) {
        if(isFileName) {
            checkout(fileName_branchName, HEADHash);
            return;
        }

        String branchName = fileName_branchName;
         if (!branches.containsKey(branchName)) {
             System.out.println("No such branch exists.");
             System.exit(0);
         }
         if(currentBranch.equals(branchName)) {
             System.out.println("No need to checkout the current branch.");
             System.exit(0);
         }

        // Takes all files in the commit at the head of the given branch, and puts them in the working directory,
        // overwriting the versions of the files that are already there if they exist.

        Commit commitOfCheckoutBranch = Commit.fromFile(branches.get(branchName));
        assert commitOfCheckoutBranch != null;

        checkoutToCommit(commitOfCheckoutBranch);

        // the given branch will now be considered the current branch (HEAD)
        currentBranch = branchName;
        HEADHash = commitOfCheckoutBranch.getHash();
    }

    /**
     * Checks If a working file is untracked in the current branch and would be overwritten by the checkout
     * @param commit the commit that may overwrite a file
     * @return true If a working file is untracked in the current branch, and it would be overwritten by the checkout
     */
    private boolean untrackedFileExists(Commit commit) {
        List<String> fileInWD = plainFilenamesIn(CWD);
        Commit HEAD = Commit.fromFile(HEADHash);
        for (String fileName : fileInWD) {

            if (!HEAD.blobs.containsKey(fileName) && commit.blobs.containsKey(fileName)) {
                return true;
            }
        }
        return false;
    }

    private void checkoutToCommit(Commit commitOfCheckoutBranch) {
        List<String> fileInWD = plainFilenamesIn(CWD);
        Commit HEAD = Commit.fromFile(HEADHash);

        // If a working file is untracked in the current branch and would be overwritten by the checkout,
        // print There is an untracked file in the way; delete it, or add and commit it first. and exit
        if (untrackedFileExists(commitOfCheckoutBranch)) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }



        for (String fileName : fileInWD) {
            // Any files that are tracked in the current branch but are not present in the checked-out branch
            // are deleted.
            if (!commitOfCheckoutBranch.blobs.containsKey(fileName)) {
                Utils.restrictedDelete(join(CWD, fileName));
                continue;
            }
            // Takes all files in the commit at the head of the given branch, and puts them in the working directory,
            // overwriting the versions of the files that are already there if they exist
            createOrChangeFileInWD(fileName, commitOfCheckoutBranch);
        }

        for (String fileName : commitOfCheckoutBranch.blobs.keySet()) {
            // Takes all files in the commit at the head of the given branch, and puts them in the working directory,
            // overwriting the versions of the files that are already there if they exist
            createOrChangeFileInWD(fileName, commitOfCheckoutBranch);
        }


        Map<String,File> filesToBeAdded = getFilesInIndex();
        // clear the staging area
        for (File file : filesToBeAdded.values()) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public void branch(String branchName) {
        if (branches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            return;
        }

        branches.put(branchName, HEADHash);
    }

    public void rm_branch(String branchName) {
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        else if (branchName.equals(currentBranch)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        branches.remove(branchName);
    }

    public void reset(String commitId) {
        Commit commit = null;

        if (commitId.length() < 40) {
            commit = Commit.fromFile(findCommitStartsWith(commitId));
        }
        else {
            commit = Commit.fromFile(commitId);
        }
        Commit HEAD = Commit.fromFile(HEADHash);
        if (commit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        checkoutToCommit(commit);
        HEADHash = commit.getHash();
        branches.put(currentBranch, commitId);
    }

    /**
     * Merges files from the given branch into the current branch.
     * @param branchName the branch to be merged with
     */
    public void merge(String branchName) {

        if (!FilesToRemove.isEmpty() || !getFilesInIndex().isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }

        Commit HEAD = Commit.fromFile(HEADHash);
        Commit branch = Commit.fromFile(branches.get(branchName));

        if (untrackedFileExists(branch)) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            return;
        }

        String splitPoint = findSplitPoint(HEAD, branch);

        assert splitPoint != null;
        if (splitPoint.equals(branch.getHash())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        if (splitPoint.equals(HEAD.getHash())) {
            System.out.println("Current branch fast-forwarded.");
            checkoutToCommit(branch);
            HEADHash = branches.get(branchName);
            branches.put(currentBranch, HEADHash);
            return;
        }

        if (splitPoint == null) System.out.println("didn't find a split point");
        Commit newCommit = new Commit(HEAD, branch, Commit.fromFile(splitPoint), currentBranch, branchName,Instant.now(), this);
        HEADHash = newCommit.getHash();
        branches.put(currentBranch, HEADHash);
    }

    private String findSplitPoint(Commit HEAD, Commit branch) {

        // BFS of HEAD
        Queue<Commit> queue = new LinkedList<>();

        Set<String> seen = new HashSet<>();
        queue.add(HEAD);
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            seen.add(commit.getHash());
            if (commit.getParent() != null) {
                queue.add(commit.getParent());
            }
            if (commit.getSecondParent() != null) {
                queue.add(commit.getSecondParent());
            }
        }

        // BFS branch
        queue.add(branch);
        while(!queue.isEmpty()) {
            Commit commit = queue.poll();
            if (seen.contains(commit.getHash())) {
                return commit.getHash();
            }
            if (commit.getParent() != null) {
                queue.add(commit.getParent());
            }
            if (commit.getSecondParent() != null) {
                queue.add(commit.getSecondParent());
            }
        }

        return null;
    }


}

