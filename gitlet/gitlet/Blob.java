package gitlet;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import static gitlet.Utils.*;

public class Blob implements Serializable {
    private String content;
    private String hash;

    public Blob(File file) {
        // read file content and store it in content instance variable
        content = readContentsAsString(file);
        // compute hash based on the content
        hash = sha1(content);

        File blobFile = join(Repository.BLOB_DIR, hash);
        try {
            if (!blobFile.exists()) {
                blobFile.createNewFile();
            }
            writeObject(blobFile, this);
        } catch (IOException e) {
            System.out.println("couldn't create blob file");
            System.exit(0);
        }

    }



    public static Blob fromFile(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        File blobFile = join(Repository.BLOB_DIR, hash);
        if (!blobFile.exists()) {
            return null;
        }
        return readObject(blobFile, Blob.class);
    }

    public String getHash() {
        return hash;
    }

    public String getContent() {
        return content;
    }


}
