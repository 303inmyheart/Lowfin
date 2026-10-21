package de.lowfin.tv;

import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class ArtworkInventoryTest {
    private File directory;
    private ArtworkInventory inventory;
    @Before public void setup() throws Exception { directory=Files.createTempDirectory("art-inventory").resolve("covers").toFile(); assertTrue(directory.mkdir()); inventory=new ArtworkInventory(directory); }
    @After public void cleanup() { for(File file:directory.getParentFile().listFiles()) { if(file.isDirectory()) for(File child:file.listFiles()) child.delete(); file.delete(); } directory.getParentFile().delete(); }
    @Test public void CleanInventorySurvivesRestartWithoutRecount() throws Exception {
        assertNull(inventory.read()); inventory.save(28024,527L*1048576);
        assertArrayEquals(new long[]{28024,527L*1048576},new ArtworkInventory(directory).read());
    }
    @Test public void InterruptedMutationNeverReturnsStaleInventory() throws Exception {
        inventory.save(4,123); inventory.beginMutation();
        assertNull(new ArtworkInventory(directory).read()); inventory.save(5,200);
        assertArrayEquals(new long[]{5,200},new ArtworkInventory(directory).read());
    }
    @Test public void ExternalDirectoryChangeRequiresRecount() throws Exception {
        inventory.save(4,123); assertTrue(directory.setLastModified(directory.lastModified()+10000));
        assertNull(new ArtworkInventory(directory).read());
    }
    @Test public void CorruptedInventoryFallsBackToRecount() throws Exception {
        inventory.save(4,123); try(FileOutputStream out=new FileOutputStream(new File(directory.getParentFile(),"covers.inventory"))) { out.write(0); }
        assertNull(new ArtworkInventory(directory).read());
    }
}
