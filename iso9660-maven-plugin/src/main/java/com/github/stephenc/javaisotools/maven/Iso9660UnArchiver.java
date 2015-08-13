package com.github.stephenc.javaisotools.maven;

import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.VFS;
import org.codehaus.plexus.archiver.AbstractUnArchiver;
import org.codehaus.plexus.archiver.ArchiverException;

import java.io.*;

public class Iso9660UnArchiver extends AbstractUnArchiver {

    private static final int COPY_BUFFER_SIZE = 1024;

    public Iso9660UnArchiver() {
    }

    public Iso9660UnArchiver(final File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void execute() throws ArchiverException {
        final FileObject root;
        try {
            root = VFS.getManager().resolveFile(getSourceUri());
        } catch (final FileSystemException ex) {
            throw new ArchiverException("Can not open root path in " + getSourceUri(), ex);
        }
        extractDirectory(root, getDestDirectory());
    }

    @Override
    protected void execute(final String path, final File outputDirectory) throws ArchiverException {
        // Open a virtual file for reading from
        final FileObject inFile;
        try {
            inFile = VFS.getManager().resolveFile(getSourceUri()).getChild(path);
        } catch (final FileSystemException ex) {
            throw new ArchiverException("Can not open " + path + " in " + getSourceUri(), ex);
        }

        // Open a real file for writing to
        final File outFile = new File(outputDirectory.getPath() + File.separatorChar + inFile.getName().getPath());

        // Copy from the virtual file to the real file
        extractFile(inFile, outFile);
    }

    private String getSourceUri() {
        return "iso://" + getSourceFile();
    }

    private void extractDirectory(final FileObject source, final File destination) throws ArchiverException {
        getLogger().debug("Descending into " + source + " using destination directory " + destination);

        final FileObject[] children;
        try {
            children = source.getChildren();
        } catch (final FileSystemException ex) {
            throw new ArchiverException("Problem getting children of " + source, ex);
        }

        for (final FileObject child : children) {

            final FileType fileType;
            final String relativeName;
            try {
                fileType = child.getType();
                relativeName = source.getName().getRelativeName(child.getName());
            } catch (final FileSystemException ex) {
                throw new ArchiverException("Problem getting information about " + child, ex);
            }

            if (fileType == FileType.FOLDER) {
                final File folder = new File(destination + File.separator + relativeName);
                if (!folder.exists()) {
                    getLogger().debug("Creating " + folder + " because it does not exist");
                    if (!folder.mkdir()) {
                        throw new ArchiverException("Could not create directory " + folder);
                    }
                }
                extractDirectory(child, folder);
            }

            if (fileType == FileType.FILE) {
                extractFile(child, new File(destination + File.separator + child.getName().getBaseName()));
            }
        }
    }

    private void extractFile(final FileObject source, final File destination) throws ArchiverException {
        try (final InputStream is = source.getContent().getInputStream();
             final OutputStream os = new FileOutputStream(destination)) {

            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int len; // Don't allow any extra bytes during the final write
            int total = 0;
            while((len = is.read(buffer)) > -1) {
                os.write(buffer, 0, len);
                total += len;
            }

            getLogger().debug("Extracted "+ source + " to " + destination + "(" + total + "B)");

        } catch (final IOException ex) {
            throw new ArchiverException(
                    "Problem copying " + source + " from " + getSourceUri() + " to " + destination, ex);
        }
    }
}
