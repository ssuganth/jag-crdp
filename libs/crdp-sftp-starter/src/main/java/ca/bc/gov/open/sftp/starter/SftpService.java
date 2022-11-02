package ca.bc.gov.open.sftp.starter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public interface SftpService {

    ByteArrayInputStream getContent(String remoteFilename);

    /**
     * @param inputFileName the content to be uploaded
     * @param remoteFileName the remote filename
     */
    void put(String inputFileName, String remoteFileName);

    void put(InputStream inputFile, String remoteFileName);

    void moveFile(String remoteFileName, String destinationFilename);

    List<String> listFiles(String remoteDirectory);

    void removeFolder(String folderPath);

    void makeFolder(String folderPath);

    boolean exists(String filePath);

    boolean isDirectory(String filePath);

    long lastModify(String filePath);
}
