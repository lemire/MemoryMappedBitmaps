import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;

import net.sourceforge.sizeof.SizeOf;

import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.RoaringBitmap;


public class Benchmark {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {						
			String dataSources[] = {"census1881.csv","census-income.csv","uscensus2000.csv","weather_sept_85.csv","wikileaks-noquotes.csv"};
			int nbRepetitions = 20;
			for(int i=0; i<dataSources.length; i++) {
				//Roaring part
				File file = File.createTempFile("roarings", "bin");
				file.deleteOnExit();
				final FileOutputStream fos = new FileOutputStream(file);
				final DataOutputStream dos = new DataOutputStream(fos);
				RealDataRetriever dataRetriever = new RealDataRetriever(args[0]);
				String dataSet = dataSources[i];
				ArrayList<Long> offsets = new ArrayList<Long>();
				//Building 200 RoaringBitmaps 
				for (int j=0; j<200; j++) {					
					int[] data = dataRetriever.fetchBitPositions(dataSet, j);
					RoaringBitmap rb = RoaringBitmap.bitmapOf(data);
					rb.trim();
					offsets.add(fos.getChannel().position());
					rb.serialize(dos);
					dos.flush();
				}
				long lastOffset = fos.getChannel().position();
				dos.close();
				RandomAccessFile memoryMappedFile = new RandomAccessFile(file, "r");
				MappedByteBuffer mbb = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, lastOffset);
				//Calculer espace de stockage sur RAM
				boolean sizeOf = true;
                try {
                        SizeOf.setMinSizeToLog(0);
                        SizeOf.skipStaticField(true);
                        // SizeOf.skipFinalField(true);
                        SizeOf.deepSizeOf(args);
                } catch (IllegalStateException e) {
                        sizeOf = false;
                        System.out
                                .println("# disabling sizeOf, run  -javaagent:lib/SizeOf.jar or equiv. to enable");

                }		
              //Calculer espace de stockage sur RAM in bytes
                long sizeRAM = 0;
                ArrayList<ImmutableRoaringBitmap> irbs = new ArrayList<ImmutableRoaringBitmap>();
				for(int k=0; k < offsets.size()-1; k++) {
					mbb.position((int)offsets.get(k).longValue());
					final ByteBuffer bb = mbb.slice();
					bb.limit((int) (offsets.get(k+1)-offsets.get(k)));
					ImmutableRoaringBitmap irb = new ImmutableRoaringBitmap(bb);
					irbs.add(irb);
					sizeRAM += (SizeOf.deepSizeOf(irb));
				}
				//Calculer espace de stockage sur disque in bytes
				long sizeDisque = file.length();
				//Effectuer l'union de 200 RoaringBitmaps
				long unionTime = 0;
				for(int rep=0; rep<nbRepetitions; rep++) {
				ImmutableRoaringBitmap irb = irbs.get(0);
				long bef = System.currentTimeMillis();
				for (int j=1; j<irbs.size()-1; j++) {
					irb = ImmutableRoaringBitmap.or(irb, irbs.get(j));
				}
				long aft = System.currentTimeMillis();
				unionTime+=aft-bef;
				}
				unionTime/=nbRepetitions;
				//Effectuer l'intersection de roaring bitmaps
				long intersectTime = 0;
				for(int rep=0; rep<nbRepetitions; rep++) {
				ImmutableRoaringBitmap irb = irbs.get(0);
				long bef = System.currentTimeMillis();
				for (int j=1; j<irbs.size()-1; j++) {
					irb = ImmutableRoaringBitmap.and(irb, irbs.get(j));
				}
				long aft = System.currentTimeMillis();
				intersectTime+=aft-bef;
				}
				intersectTime/=nbRepetitions;
				//Calculer temps de récupération des bits positifs
				long scanTime = 0;
				for(int rep=0; rep<nbRepetitions; rep++) {
				for(int k=0; k<irbs.size(); k++){
					ImmutableRoaringBitmap irb = irbs.get(k);
					Iterator<Integer> it = irb.iterator();
					long bef = System.currentTimeMillis();
					while(it.hasNext())
					 {
						it.next();
					 }
					long aft = System.currentTimeMillis();
					scanTime+=aft-bef;
				}
				}
				scanTime/=nbRepetitions;
				System.out.println("***************************");
				System.out.println("DataSet :: "+dataSet);
				System.out.println("***************************");
				System.out.println("RAM Size = "+sizeRAM+" bytes");
				System.out.println("Disque Size = "+sizeDisque+" bytes");
				System.out.println("Unions time = "+unionTime+" ms");
				System.out.println("Intersctions time = "+intersectTime+" ms");
				System.out.println("Scans time = "+scanTime+" ms");
				//ConciseSet part
				
			}			
		} catch (IOException e) {e.printStackTrace();}
	}

}
