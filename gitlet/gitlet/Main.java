package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author AGX
 */

public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // args is empty
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        Repository Repo;
        switch (firstArg) {
            case "init":

                if (Repository.doesGitletDirExists()) {
                    System.out.println("A Gitlet version-control system already exists "
                            + "in the current directory.");
                    System.exit(0);
                }
                Repository newRepo = new Repository();
                newRepo.init();
                break;
            case "add":
                // the `add [filename]` command
                Repo = Repository.fromFile();
                if (args.length < 2) {
                    System.out.println("Please enter a File name");
                    System.exit(0);
                }

                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.add(args[1]);
                Repo.save();
                break;

            case "commit":
                // the `commit -m [message]` command
                if (args.length < 2 || args[1].isEmpty()) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.commit(args[1]);
                Repo.save();
                break;

            case "rm":
                // java gitlet.Main rm [file name]
                if (args.length < 2) {
                    System.exit(0);
                }
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.rm(args[1]);
                Repo.save();
                break;

            case "log":
                // java gitlet.Main log
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.log();
                break;

            case "global-log":
                // java gitlet.Main global-log
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.global_log();
                break;

            case "find":
                // java gitlet.Main find [commit message]
                if (args.length < 2) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }

                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.find(args[1]);
                break;

            case "status":
                // java gitlet.Main status
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.status();
                break;

            case "checkout":
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }

                // java gitlet.Main checkout [branch name]
                if (args.length == 2) {
                    Repo.checkout(args[1], false);
                } else if (args.length == 3) {
                    // java gitlet.Main checkout -- [file name]
                    if (!args[1].equals("--")) {
                        System.out.println("Incorrect operands.");
                        break;
                    }
                    Repo.checkout(args[2], true);
                } else if (args.length == 4) {
                    // java gitlet.Main checkout [commit id] -- [file name]
                    if (!args[2].equals("--")) {
                        System.out.println("Incorrect operands.");
                        break;
                    }
                    Repo.checkout(args[3], args[1]);
                }
                Repo.save();
                break;

            case "branch":
                if (args.length < 2) {
                    return;
                }
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.branch(args[1]);
                Repo.save();
                break;

            case "reset":
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.reset(args[1]);
                Repo.save();
                break;

            case "rm-branch":
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.rm_branch(args[1]);
                Repo.save();
                break;

            case "merge":
                Repo = Repository.fromFile();
                if (Repo == null) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                Repo.merge(args[1]);
                Repo.save();
                break;

            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
        }
    }
}
