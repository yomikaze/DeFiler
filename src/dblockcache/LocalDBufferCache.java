package dblockcache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import common.Constants;
import virtualdisk.Inode;
import virtualdisk.LocalVirtualDisk;
import virtualdisk.VirtualDisk;

public class LocalDBufferCache extends DBufferCache {

	private VirtualDisk myDisk;
	private int myCacheSize;
	private Queue<Integer> leastRecentlyUsed;
	private Map<Integer, DBuffer> DBufferMap;
	private Inode[] myInodes;
	
	public LocalDBufferCache(int cacheSize, VirtualDisk disk) {
		super(cacheSize);
		myCacheSize = cacheSize;
		myDisk = disk;
		DBufferMap = new HashMap<Integer, DBuffer>();
		leastRecentlyUsed = new LinkedList<Integer>();
		
		Thread virtualDiskThread = new Thread(myDisk);
		virtualDiskThread.start();
	}

	@Override
	public synchronized DBuffer getBlock(int blockID) {
		if(DBufferMap.containsKey(blockID)) {
			LocalDBuffer dbuf = (LocalDBuffer) DBufferMap.get(blockID);
			dbuf.setBusy(true);
			if (leastRecentlyUsed.contains(blockID)) {
				leastRecentlyUsed.remove(blockID);
			}
			leastRecentlyUsed.add(blockID);
			return dbuf;
		}
		LocalDBuffer dbuf = new LocalDBuffer(blockID, Constants.BLOCK_SIZE, myDisk);
		dbuf.setBusy(true);
		
		// eviction
		if (DBufferMap.size() == myCacheSize){
			Iterator<Integer> iterator = leastRecentlyUsed.iterator();
	        while (iterator.hasNext()) {
	            int current = iterator.next();
	            if(!DBufferMap.get(current).isBusy()) {
	                iterator.remove();
	                DBufferMap.remove(current);
	                break;
	            }
	        }
        }

        if (!dbuf.checkValid()) {
            dbuf.startFetch();
            dbuf.waitValid();
        }
		
		DBufferMap.put(blockID, dbuf);
		leastRecentlyUsed.add(blockID);
		return dbuf;
	}

	@Override
	public synchronized void releaseBlock(DBuffer buf) {
		((LocalDBuffer) buf).setBusy(false);
		//signal??	
	}
	
	public void shutdown() {
		((LocalVirtualDisk) myDisk).stopDisk();
	}
	
	public synchronized void getInodes(Inode[] inodes) {
		myInodes = inodes;
	}

	@Override
	public synchronized void sync() {
		for (Integer id : DBufferMap.keySet()) {
			LocalDBuffer dbuf = (LocalDBuffer) DBufferMap.get(id);
			if (!dbuf.checkClean()) {
//				try {
//					//myDisk.writeInode(id, myInodes[id]);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				
				dbuf.startPush();
				dbuf.waitClean();
			}
		}
		
		notifyAll();
	}
}
