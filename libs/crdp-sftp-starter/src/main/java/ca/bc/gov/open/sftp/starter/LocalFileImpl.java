package ca.bc.gov.open.sftp.starter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalFileImpl implements SftpService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public LocalFileImpl() {}

    @Override
    public ByteArrayInputStream getContent(String remoteFilename) {
        return null;
    }

    @Override
    public void put(String inputFileName, String remoteFileName) {}

    @Override
    public void put(InputStream input, String remoteFileName) {}

    /**
     * Move the file to a destination
     *
     * @param sourceFileName
     * @param destinationFilename
     * @throws IOException
     */
    @Override
    public void moveFile(String sourceFileName, String destinationFilename) {
        try {
            File source = new File(sourceFileName);
            if (source.isDirectory()) {
                moveFolderSvc(sourceFileName, destinationFilename);
            } else {
                moveFileSvc(sourceFileName, destinationFilename);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public List<String> listFiles(String directory) {
        File target = new File(directory);
        List<String> result = new ArrayList<>();
        for (File f : target.listFiles()) {
            result.add(f.getPath());
        }
        return result;
    }

    @Override
    public void removeFolder(String folderPath) {
        File target = new File(folderPath);
        try {
            if (target.exists()) {
                FileUtils.deleteDirectory(target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void makeFolder(String folderPath) {
        File target = new File(folderPath);
        // The folderPath must IN a folder owned by wmadmin OR be in a folder with o-rwx permissions
        // set.
        try {
            FileUtils.forceMkdir(target);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean exists(String filePath) {
        File target = new File(filePath);
        return target.exists();
    }

    @Override
    public boolean isDirectory(String filePath) {
        File target = new File(filePath);
        return target.isDirectory();
    }

    @Override
    public long lastModify(String filePath) {
        File target = new File(filePath);
        return target.lastModified();
    }

    /** The primary method for the Java service to move a single file */
    public static final void moveFileSvc(String filePath, String targetFolder) {
        File source = new File(filePath);
        File destFolder = new File(targetFolder);

        try {
            FileUtils.moveFile(source, destFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** The primary method for the Java service to move a single folder */
    public static final void moveFolderSvc(String folderPath, String targetFolder) {
        File source = new File(folderPath);
        File dest = new File(targetFolder);
        try {
            FileUtils.copyDirectory(source, dest);
            FileUtils.deleteDirectory(source);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
