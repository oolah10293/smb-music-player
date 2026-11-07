package org.oolah.s24backup;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.EnumSet;

public final class SmbTransport {
    private SmbTransport() { }

    public interface Action<T> {
        T run(DiskShare share) throws Exception;
    }

    public static <T> T withShare(BackupConfig.Server config, Action<T> action) throws Exception {
        config.validate();
        try (SMBClient client = new SMBClient();
             Connection connection = client.connect(config.host.trim(), config.port);
             Session session = connection.authenticate(new AuthenticationContext(
                     config.user.trim(), config.password.toCharArray(), config.domain.trim()));
             DiskShare share = (DiskShare) session.connectShare(config.share.trim())) {
            return action.run(share);
        }
    }

    public static void test(BackupConfig.Server config) throws Exception {
        withShare(config, share -> null);
    }

    public static void ensureDirectories(DiskShare share, String folder) {
        String current = "";
        for (String part : RemotePaths.safe(folder).split("\\\\")) {
            if (part.isEmpty()) continue;
            current = current.isEmpty() ? part : current + "\\" + part;
            if (!share.folderExists(current)) share.mkdir(current);
        }
    }

    public static long remoteSize(DiskShare share, String dest) {
        return share.getFileInformation(dest).getStandardInformation().getEndOfFile();
    }

    public static void upload(DiskShare share, java.io.File source, String target, Interrupt check)
            throws Exception {
        target = RemotePaths.safe(target);
        int sep = target.lastIndexOf('\\');
        if (sep >= 0) ensureDirectories(share, target.substring(0, sep));
        String temporary = target + ".s24backup.part";
        long originalSize = source.length();
        long originalMtime = source.lastModified();

        try (com.hierynomus.smbj.share.File destination = share.openFile(
                temporary,
                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE,
                           SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions.class))) {
            long copied = 0L;
            byte[] buffer = new byte[128 * 1024];
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = destination.getOutputStream()) {
                int len;
                while ((len = in.read(buffer)) != -1) {
                    check.check();
                    out.write(buffer, 0, len);
                    copied += len;
                }
                out.flush();
            }
            if (copied != originalSize || source.length() != originalSize
                    || source.lastModified() != originalMtime)
                throw new IOException("Source changed during backup: " + source.getName());
            check.check();
            // Renaming only happens after the complete upload; failed transfers leave
            // the original backed-up file intact (and a .part file for the next retry).
            destination.rename(target, true);
        }
    }

    public interface Interrupt {
        void check() throws Exception;
    }
}
