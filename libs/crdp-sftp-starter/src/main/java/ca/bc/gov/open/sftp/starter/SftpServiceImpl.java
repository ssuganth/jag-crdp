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

public class SftpServiceImpl implements SftpService {

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

    public ByteArrayInputStream getContent(String remoteFilename) {
        String sftpRemoteFilename = getFilePath(remoteFilename);

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
     * Move the file to a destination
     *
     * @param remoteFileName
     * @param destinationFilename
     * @throws StarterSftpException
     */
    @Override
    public void moveFile(String remoteFileName, String destinationFilename) {
        String sftpRemoteFilename = getFilePath(remoteFileName);
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

    @Override
    public void put(InputStream inputFile, String remoteFileName) {
        String sftpRemoteFilename = getFilePath(remoteFileName);

        executeSftpFunction(
                channelSftp -> {
                    channelSftp.put(inputFile, sftpRemoteFilename);
                    logger.debug("Successfully uploaded file [{}]", remoteFileName);
                });
    }

    /**
     * Returns a list of file
     *
     * @param remoteDirectory
     * @return
     */
    @Override
    public List<String> listFiles(String remoteDirectory) {
        String sftpRemoteDirectory = getFilePath(remoteDirectory);
        List<String> result = new ArrayList<>();

        executeSftpFunction(
                channelSftp -> {
                    Vector fileList = channelSftp.ls(sftpRemoteDirectory);

                    for (int i = 0; i < fileList.size(); i++) {
                        logger.debug("Attempting to list files in [{}]", remoteDirectory);
                        ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) fileList.get(i);
                        logger.debug("Successfully to list files in [{}]", remoteDirectory);
                        result.add(lsEntry.getFilename());
                    }
                });

        return result;
    }

    @Override
    public void removeFolder(String folderPath) {
        executeSftpFunction(
                channelSftp -> {
                    channelSftp.rm(folderPath);
                    logger.debug("Successfully removed folder [{}]", folderPath);
                });
    }

    @Override
    public void makeFolder(String folderPath) {
        executeSftpFunction(
                channelSftp -> {
                    channelSftp.mkdir(folderPath);
                    logger.debug("Successfully created folder [{}]", folderPath);
                });
    }

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

    private String getFilePath(String remotePath) {
        return FilenameUtils.separatorsToUnix(
                StringUtils.isNotBlank(sftpProperties.getRemoteLocation())
                        ? Paths.get(sftpProperties.getRemoteLocation(), remotePath).toString()
                        : Paths.get(remotePath).toString());
    }
}
