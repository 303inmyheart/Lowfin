package de.lowfin.tv;

import android.net.Uri;
import androidx.media3.extractor.*;
import androidx.media3.extractor.ts.TsExtractor;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Generated TS headers only: no private media or third-party video is part of this fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class TsDurationTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static final class Output implements ExtractorOutput {
        SeekMap map;
        @Override public TrackOutput track(int id,int type) {return new DummyTrackOutput();}
        @Override public void endTracks() {}
        @Override public void seekMap(SeekMap value) {map=value;}
    }
    static SeekMap probe(File file,ExtractorsFactory factory) throws Exception {
        try(RandomAccessFile data=new RandomAccessFile(file,"r")) {
            Extractor selected=null;DefaultExtractorInput input=null;
            for(Extractor extractor:factory.createExtractors(Uri.parse("file:///fixture.mp4"),Collections.singletonMap("Content-Type",Collections.singletonList("video/mp4")))) {
                data.seek(0);input=new DefaultExtractorInput(data::read,0,data.length());
                if(extractor.sniff(input)) {selected=extractor;break;}extractor.release();
            }
            assertNotNull(selected);assertTrue("Content sniffing must identify mislabeled transport streams",selected instanceof TsExtractor||selected instanceof AdaptiveTsExtractor);
            Output output=new Output();selected.init(output);PositionHolder holder=new PositionHolder();
            try {
                for(int i=0;i<20000&&output.map==null;i++) {
                    int result=selected.read(input,holder);if(result==Extractor.RESULT_END_OF_INPUT) break;
                    if(result==Extractor.RESULT_SEEK) {data.seek(holder.position);input=new DefaultExtractorInput(data::read,holder.position,data.length());}
                }
                assertNotNull("Extractor must publish a seek map",output.map);return output.map;
            } finally {selected.release();}
        }
    }
    private static byte[] section(byte[] body) {
        int crc=-1;
        for(byte value:body) {crc^=(value&255)<<24;for(int bit=0;bit<8;bit++) crc=(crc<<1)^((crc&0x80000000)!=0?0x04c11db7:0);}
        byte[] result=Arrays.copyOf(body,body.length+4);for(int i=0;i<4;i++) result[body.length+i]=(byte)(crc>>>(24-i*8));return result;
    }
    private static void table(byte[] data,int packet,int pid,byte[] body) {
        int start=packet*188;data[start]=0x47;data[start+1]=(byte)(0x40|(pid>>8));data[start+2]=(byte)pid;data[start+3]=0x10;data[start+4]=0;
        byte[] table=section(body);System.arraycopy(table,0,data,start+5,table.length);
    }
    private static void pcr(byte[] data,int packet,long seconds) {
        int start=packet*188;data[start]=0x47;data[start+1]=1;data[start+2]=0;data[start+3]=0x20;data[start+4]=(byte)183;data[start+5]=0x10;
        long value=seconds*90000;data[start+6]=(byte)(value>>25);data[start+7]=(byte)(value>>17);data[start+8]=(byte)(value>>9);data[start+9]=(byte)(value>>1);data[start+10]=(byte)(((value&1)<<7)|0x7e);data[start+11]=0;
    }
    @Test public void AudioOnlyTailBeyondDefaultSearchWindowDoesNotDisableDurationAndSeeking() throws Exception {
        for(int scenario:new int[]{1405,2400,3600,10000,20000,30000,-3600}) {
            int packets=Math.abs(scenario);boolean noPcr=scenario<0;
            byte[] data=new byte[packets*188];Arrays.fill(data,(byte)0xff);
            for(int i=0;i<packets;i++) {int start=i*188;data[start]=0x47;data[start+1]=0x1f;data[start+2]=(byte)0xff;data[start+3]=0x10;}
            table(data,0,0,new byte[]{0,(byte)0xb0,13,0,1,(byte)0xc1,0,0,0,1,(byte)0xef,(byte)0xff});
            table(data,1,4095,new byte[]{2,(byte)0xb0,18,0,1,(byte)0xc1,0,0,(byte)0xe1,0,(byte)0xf0,0,0x1b,(byte)0xe1,0,(byte)0xf0,0});
            if(!noPcr) {pcr(data,2,1);pcr(data,1400,1201);}
            File file=temporary.newFile("fixture-"+scenario+".mp4");Files.write(file.toPath(),data);
            boolean easy=packets==1405;
            SeekMap original=probe(file,new DefaultExtractorsFactory());assertEquals(easy,original.isSeekable());if(!easy) assertTrue(original.getDurationUs()<=0);
            if(packets==3600) assertFalse("A longer audio-only tail also exceeds the previous 256 KiB budget",probe(file,new DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(256*1024)).isSeekable());
            SeekMap fixed=probe(file,LocalPlayback.extractors(false));
            if(packets==30000||noPcr) {assertFalse(fixed.isSeekable());assertTrue(fixed.getDurationUs()<=0);}
            else {assertTrue(fixed.isSeekable());assertEquals(1200000000L,fixed.getDurationUs());}
            SeekMap live=probe(file,LocalPlayback.extractors(true));assertEquals("Live TV retains its original search budget",easy,live.isSeekable());
        }
    }
}
