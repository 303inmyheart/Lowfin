package de.lowfin.tv;

import android.util.AtomicFile;
import java.io.*;

/** Small durable inventory; an interrupted mutation requires a real recount. */
final class ArtworkInventory {
    private final File directory,dirty;
    private final AtomicFile index;
    ArtworkInventory(File directory) {
        this.directory=directory;
        index=new AtomicFile(new File(directory.getParentFile(),directory.getName()+".inventory"));
        dirty=new File(directory.getParentFile(),directory.getName()+".dirty");
    }
    long[] read() {
        if(dirty.exists()) return null;
        try(DataInputStream input=new DataInputStream(index.openRead())) {
            if(input.readInt()!=1||input.readLong()!=directory.lastModified()) return null;
            long files=input.readLong(),bytes=input.readLong();
            return files<0||bytes<0?null:new long[]{files,bytes};
        } catch(IOException e) { return null; }
    }
    void beginMutation() throws IOException {
        if(dirty.exists()) return;
        try(FileOutputStream output=new FileOutputStream(dirty)) { output.getFD().sync(); }
    }
    void save(long files,long bytes) throws IOException {
        FileOutputStream stream=index.startWrite();
        try {
            DataOutputStream output=new DataOutputStream(stream);
            output.writeInt(1); output.writeLong(directory.lastModified()); output.writeLong(files); output.writeLong(bytes); output.flush();
            index.finishWrite(stream);
            if(dirty.exists()&&!dirty.delete()) throw new IOException("Artwork inventory remains dirty");
        } catch(IOException e) { index.failWrite(stream); throw e; }
    }
}
