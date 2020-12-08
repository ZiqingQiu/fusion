package org.apache.hadoop.fs.smb;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Properties;

public class SmbFileSystem extends FileSystem {
    private static final Log LOG = LogFactory.getLog(SmbFileSystem.class);
    public static final String FS_SMB_USER_PREFIX = "fs.smb.user.";
    public static final String FS_SMB_PASS_PREFIX = "fs.smb.pass.";
    public static final String FS_SMB_DOMAIN_PREFIX = "fs.smb.domain.";
    public static final String FS_SMB_HOST = "fs.smb.host";
    public static final int DEFAULT_BLOCK_SIZE = 4 * 1024;

    private URI uri;
    private String host;
    private PropertyConfiguration propertyConfiguration;
    private Path workingDir;

    @Override
    public String getScheme() {
        return "smb";
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void initialize(URI uri, Configuration conf) throws IOException {
        super.initialize(uri, conf);
        host = uri.getHost();
        if (host == null) {
            host = conf.get(FS_SMB_HOST);
        } else {
            conf.set(FS_SMB_HOST, host);
        }
        if (host == null) {
            throw new IOException("Invalid host specified");
        }
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int semiIndex = userInfo.indexOf(';');
            if (semiIndex > 0) {
                conf.set(FS_SMB_DOMAIN_PREFIX + host, userInfo.substring(0, semiIndex));
            }
            semiIndex++;
            int colonIndex = userInfo.indexOf(':', semiIndex);
            if (colonIndex > 0) {
                conf.set(FS_SMB_USER_PREFIX + host, userInfo.substring(semiIndex, colonIndex));
                conf.set(FS_SMB_PASS_PREFIX + host, userInfo.substring(colonIndex + 1));
            } else {
                conf.set(FS_SMB_USER_PREFIX + host, userInfo.substring(semiIndex));
            }
        }
        setConf(conf);
        Properties jcifsProperties  = new Properties();
        jcifsProperties.putAll(conf.getValByRegex("^jcifs.*"));
        this.propertyConfiguration = new PropertyConfiguration(jcifsProperties);
        this.uri = uri;
    }

    @Override
    public FSDataInputStream open(Path path, int bufferSize) throws IOException {
        connect();
        SmbFile smbFile = new SmbFile(getPathName(path), cifsContext);
        SmbRandomAccessFile raf = new SmbRandomAccessFile(smbFile, "r");
        return new FSDataInputStream(new SmbInputStream(raf, statistics));
    }

    @Override
    public FSDataOutputStream create(Path f, FsPermission permission, boolean overwrite, int bufferSize,
                                     short replication, long blockSize, Progressable progress) throws IOException {
        try {
            connect();
            FileStatus status;
            try {
                status = getFileStatus(f);
            } catch(FileNotFoundException e) {
                status = null;
            }
            if (status != null) {
                if (overwrite && !status.isDirectory()) {
                    delete(f, false);
                } else {
                    throw new FileAlreadyExistsException("File already exists: " + f);
                }
            }
            Path parent = f.getParent();
            if (parent == null || !mkdirs(parent, FsPermission.getDirDefault())) {
                parent = (parent == null) ? new Path("/") : parent;
            }
            SmbFile smbFile = new SmbFile(getPathName(f), cifsContext);
            smbFile.createNewFile();
            OutputStream os = smbFile.getOutputStream();
            return new FSDataOutputStream(os, statistics);
        } catch (Exception e) {
            throw new IOException("create(): failed to create " + e.getLocalizedMessage());
        }
    }

    @Override
    public FSDataOutputStream append(Path f, int bufferSize, Progressable progress) throws IOException {
        try {
            connect();
            SmbFile smbFile = new SmbFile(getPathName(f), cifsContext);
            OutputStream os = smbFile.getOutputStream();
            return new FSDataOutputStream(os, statistics);
        } catch (Exception e) {
            throw new IOException("append(): failed to append " + e.getLocalizedMessage());
        }
    }

    @Override
    public boolean rename(Path src, Path dst) throws IOException {
        connect();
        try {
            SmbFile smbFileSrc = new SmbFile(src.getName(), cifsContext);
            SmbFile smbFileDst = new SmbFile(dst.getName(), cifsContext);
            smbFileSrc.renameTo(smbFileDst);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean delete(Path f, boolean recursive) throws IOException {
        connect();
        try {
            try {
                FileStatus fileStat = getFileStatus(f);
                if (fileStat.isFile()) {
                    (new SmbFile(getPathName(f), cifsContext)).delete();
                    return true;
                }
            } catch (FileNotFoundException e) {
                return false;
            }
            FileStatus[] dirEntries = listStatus(f);
            if (dirEntries.length > 0 && !recursive) {
                throw new IOException("Directory: " + f + " is not empty");
            }
            for (FileStatus dirEntry: dirEntries) {
                delete(new Path(f, dirEntry.getPath()), recursive);
            }
            (new SmbFile(getPathName(f), cifsContext)).delete();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileStatus[] listStatus(Path f) throws FileNotFoundException, IOException {
        try {
            connect();
            FileStatus fileStat = getFileStatus(f);
            if (fileStat.isFile()) {
                return new FileStatus[]{fileStat};
            }
            SmbFile[] smbFiles = (new SmbFile(getPathName(f), cifsContext)).listFiles();
            FileStatus[] fileStats = new FileStatus[smbFiles.length];
            for (int i = 0; i < smbFiles.length; i++) {
                fileStats[i] = getFileStatus(smbFiles[i], new Path(smbFiles[i].getPath()));
            }
            return fileStats;
        } catch(IOException e) {
            throw new IOException("unable to list status: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void setWorkingDirectory(Path new_dir) {
        workingDir = new_dir;
    }

    @Override
    public Path getWorkingDirectory() {
        return workingDir == null ? getHomeDirectory() : workingDir;
    }

    @Override
    public Path getHomeDirectory() {
        return new Path("/");
    }

    @Override
    public boolean mkdirs(Path f, FsPermission permission) throws IOException {
        try {
            connect();
            if (!exists(f)) {
                Path parent = f.getParent();
                if (parent != null) {
                    mkdirs(parent, FsPermission.getDirDefault());
                }
                SmbFile smbFile = new SmbFile(getPathName(f), cifsContext);
                smbFile.mkdir();
            } else if (isFile(f)) {
                throw new ParentNotDirectoryException(
                        String.format("Can't make directory for path %s since it is a file", getPathName(f)));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileStatus getFileStatus(Path f) throws IOException {
        connect();
        SmbFile smbFile = new SmbFile(getPathName(f), cifsContext);
        return getFileStatus(smbFile, f);
    }

    private FileStatus getFileStatus(SmbFile smbFile, Path file) throws IOException {
        long length = smbFile.length();
        boolean isDir = smbFile.isDirectory();
        int blockReplication = 1;
        long blockSize = DEFAULT_BLOCK_SIZE;
        long modTime = smbFile.getDate();
        return new FileStatus(length, isDir, blockReplication, blockSize, modTime, file);
    }

    private String getPathName(Path file) {
        return "smb://" + host + file.toUri().getPath();
    }

    CIFSContext cifsContext;

    private void connect() {
        if (cifsContext == null) {
            cifsContext = reconnect();
        }
    }

    private CIFSContext reconnect() {
        BaseContext baseCxt = new BaseContext(propertyConfiguration);
        Configuration conf = getConf();
        String host = conf.get(FS_SMB_HOST);
        String domain = conf.get(FS_SMB_DOMAIN_PREFIX + host);
        String user = conf.get(FS_SMB_USER_PREFIX + host);
        String pass = conf.get(FS_SMB_PASS_PREFIX + host);
        return baseCxt.withCredentials(new NtlmPasswordAuthenticator(domain, user, pass));
    }
}
