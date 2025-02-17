package ca.bc.gov.open.sftp.starter;

import com.jcraft.jsch.*;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpServiceImpl implements FileService {

    interface SftpFunction {
        void exec(ChannelSftp channelSftp) throws SftpException;
    }

    public static final int BUFFER_SIZE = 8000;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final JschSessionProvider jschSessionProvider;

    private final SftpProperties sftpProperties;

    public SftpServiceImpl(JschSessionProvider jschSessionProvider, SftpProperties sftpProperties) {
        this.jschSessionProvider = jschSessionProvider;
        this.sftpProperties = sftpProperties;
    }

    /**
     * Get file content in byte array
     *
     * @param filename
     */
    @Override
    public ByteArrayInputStream getContent(String filename) {
        String sftpRemoteFilename = getFilePath(filename);

        ByteArrayInputStream result = null;
        byte[] buff = new byte[BUFFER_SIZE];

        Session session = null;

        try (ByteArrayOutputStream bao = new ByteArrayOutputStream()) {

            executeSftpFunction(
                    channelSftp -> {
                        try {

                            int bytesRead;

                            logger.debug("Attempting to get remote file [{}]", sftpRemoteFilename);
                            InputStream inputStream = channelSftp.get(sftpRemoteFilename);
                            logger.debug("Successfully get remote file [{}]", sftpRemoteFilename);

                            while ((bytesRead = inputStream.read(buff)) != -1) {
                                bao.write(buff, 0, bytesRead);
                            }

                        } catch (IOException e) {
                            throw new StarterSftpException(e.getMessage(), e.getCause());
                        }
                    });

            byte[] data = bao.toByteArray();

            try (ByteArrayInputStream resultBao = new ByteArrayInputStream(data)) {
                result = resultBao;
            }

        } catch (IOException e) {
            throw new StarterSftpException(e.getMessage(), e.getCause());
        }

        return result;
    }

    /**
     * Put the file to a destination
     *
     * @param inputFileName
     * @param remoteFileName
     */
    @Override
    public void put(String inputFileName, String remoteFileName) {
        String sftpRemoteFilename;
        if (remoteFileName.contains("objstr_zd/")) {
            sftpRemoteFilename =
                    sftpProperties.getRemoteLocation()
                            + remoteFileName.substring(
                                    remoteFileName.indexOf("objstr_zd/") + "objstr_zd/".length());
        } else {
            sftpRemoteFilename = getFilePath(remoteFileName);
        }

        executeSftpFunction(
                channelSftp -> {
                    channelSftp.put(inputFileName, sftpRemoteFilename);
                    logger.debug("Successfully uploaded file [{}]", remoteFileName);
                });
    }

    /**
     * Put the file with input stream to a destination
     *
     * @param inputStream
     * @param remoteFileName
     */
    @Override
    public void put(InputStream inputStream, String remoteFileName) {
        String sftpRemoteFilename = getFilePath(remoteFileName);

        executeSftpFunction(
                channelSftp -> {
                    channelSftp.put(inputStream, sftpRemoteFilename);
                    logger.debug("Successfully uploaded file [{}]", remoteFileName);
                });
    }

    /**
     * Move the file to a destination
     *
     * @param sourceFileName
     * @param destinationFilename
     */
    @Override
    public void moveFile(String sourceFileName, String destinationFilename) {
        String sftpRemoteFilename = getFilePath(sourceFileName);
        String sftpDestinationFilename = getFilePath(destinationFilename);

        executeSftpFunction(
                channelSftp -> {
                    channelSftp.rename(sftpRemoteFilename, sftpDestinationFilename);
                    logger.debug(
                            "Successfully renamed files on the sftp server from {} to {}",
                            sftpRemoteFilename,
                            sftpDestinationFilename);
                });
    }

    /**
     * List all files and folders under the directory
     *
     * @param directory
     * @return list of files and folders names
     */
    @Override
    public List<String> listFiles(String directory) {
        String sftpRemoteDirectory = getFilePath(directory);
        List<String> result = new ArrayList<>();

        executeSftpFunction(
                channelSftp -> {
                    Vector fileList = channelSftp.ls(sftpRemoteDirectory);

                    for (int i = 0; i < fileList.size(); i++) {
                        logger.debug("Attempting to list files in [{}]", directory);
                        ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) fileList.get(i);
                        logger.debug("Successfully to list files in [{}]", directory);
                        result.add(lsEntry.getFilename());
                    }
                });

        return result;
    }

    /**
     * Remove the directory under the folder path
     *
     * @param folderPath
     */
    @Override
    public void removeFolder(String folderPath) {
        executeSftpFunction(
                channelSftp -> {
                    channelSftp.rm(folderPath);
                    logger.debug("Successfully removed folder [{}]", folderPath);
                });
    }

    /**
     * Create a folder
     *
     * @param folderPath
     */
    @Override
    public void makeFolder(String folderPath) {
        executeSftpFunction(
                channelSftp -> {
                    channelSftp.mkdir(folderPath);
                    logger.debug("Successfully created folder [{}]", folderPath);
                });
    }

    /**
     * Check if a file exists
     *
     * @param filePath
     * @return true/false of file existence
     */
    @Override
    public boolean exists(String filePath) {
        AtomicBoolean result = new AtomicBoolean(false);
        executeSftpFunction(
                channelSftp -> {
                    try {
                        channelSftp.lstat(filePath);
                        result.set(true);
                    } catch (SftpException e) {
                        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                            result.set(false);
                        } else {
                            logger.error(e.getMessage());
                        }
                    }
                    logger.debug(filePath + " is found");
                });
        return result.get();
    }

    /**
     * Check if a file is a directory/folder
     *
     * @param filePath
     * @return true/false of if file is a directory
     */
    @Override
    public boolean isDirectory(String filePath) {
        AtomicBoolean result = new AtomicBoolean(false);
        executeSftpFunction(
                channelSftp -> {
                    try {
                        result.set(channelSftp.lstat(filePath).isDir());
                        logger.debug(
                                filePath
                                        + " is a directory is "
                                        + channelSftp.lstat(filePath).isDir());
                    } catch (SftpException e) {
                        logger.error(e.getMessage());
                    }
                });
        return result.get();
    }

    /**
     * Get the last datetime timestamp of a file
     *
     * @param filePath
     * @return long timestamp
     */
    @Override
    public long lastModify(String filePath) {
        AtomicLong result = new AtomicLong();
        executeSftpFunction(
                channelSftp -> {
                    try {
                        result.set(channelSftp.lstat(filePath).getMTime());
                        logger.debug(
                                "Last modified of "
                                        + filePath
                                        + " is "
                                        + channelSftp.lstat(filePath).getMtimeString());
                    } catch (SftpException e) {
                        logger.error(e.getMessage());
                    }
                });
        return result.get();
    }

    /** The primary method for executing sftp apis from ChannelSftp lib */
    private void executeSftpFunction(SftpFunction sftpFunction) {
        ChannelSftp channelSftp = null;
        Session session = null;

        try {
            session = jschSessionProvider.getSession();

            logger.debug("Attempting to open sftp channel");
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            logger.debug("Successfully connected to sftp server");

            sftpFunction.exec(channelSftp);

        } catch (JSchException | SftpException e) {
            throw new StarterSftpException(e.getMessage(), e.getCause());
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();

            jschSessionProvider.closeSession(session);
        }
    }

    /** The primary method for getting remote file's full path */
    private String getFilePath(String remotePath) {
        return FilenameUtils.separatorsToUnix(
                StringUtils.isNotBlank(sftpProperties.getRemoteLocation())
                        ? Paths.get(sftpProperties.getRemoteLocation(), remotePath).toString()
                        : Paths.get(remotePath).toString());
    }
}
