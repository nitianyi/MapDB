package org.mapdb;

import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class StoreDirectFreeSpaceTest {

    final long max = 100000;

    final Map<Long,Deque<Long>> longStacks = new TreeMap <Long,Deque<Long>>();

    /** mock longStacks so their page allocations wont mess up tests */
    StoreDirect stub = new  StoreDirect(Volume.memoryFactory(false,max),false,false,5,false,max){


        private Deque<Long> stackList(long ioList) {
            if(longStacks.get(ioList)==null) longStacks.put(ioList, new LinkedList<Long>());
            return longStacks.get(ioList);
        }

        @Override
        protected long longStackTake(long ioList) {
            Long r = stackList(ioList).pollLast();
            return r!=null?r:0;
        }


        @Override
        protected void longStackPut(long ioList, long offset) {
            stackList(ioList).add(offset);
        }
    };

    void fill(long... n){
        for(int i=0;i<n.length;i+=2){
            stub.longStackPut(n[i],n[i+1]);
        }
    }


    void check( long... n){
        long[] a = stub.physAllocate((int) n[0],true);
        long[] b = new long[n.length];

        for(int i=0;i<a.length;i++){
            long size =  (a[i]&StoreDirect.MASK_SIZE)>>>48; //size
            b[i*2+1] = size;
            b[0]+=size -  (i==a.length-1 ? 0: 8);
            b[i*2+2] = a[i] & StoreDirect.MASK_OFFSET; //offset
        }

        assertArrayEquals(n, b);
    }

    long size(long i){
        return StoreDirect.size2ListIoRecid(i);
    }

    @Test
    public void simpleTake(){
        fill(1,2);
        assertEquals(2, stub.longStackTake(1));
    }

    @Test
    public void simpleSpaceAlloc(){
        long ioList = size(16);
        fill(ioList,32);
        check(16, 16,32);
    }

    @Test
    public void simpleGrow(){
        check(32,32,16);
        check(16,16,48);
    }

    @Test
    public void largeGrow(){
        int size = StoreDirect.MAX_REC_SIZE+100;
        check(size,  StoreDirect.MAX_REC_SIZE, 16, 108, 16+StoreDirect.MAX_REC_SIZE+1);
    }

    @Test public void reuse_after_full(){
        stub.physSize = max;
        fill(size(1600),1000);
        check(1600,1600,1000);
    }

    //TODO this is not possible with current store design, need var-size LongStack pages
    @Ignore
    @Test public void split_after_full(){
        stub.physSize = max;
        fill(size(3200),1000);
        check(1600,1600,1000);
        check(1600,1600,2600);
        assertLongStacksEmpty();
    }

    void assertLongStacksEmpty() {
        for(Deque d:longStacks.values()){
            if(!d.isEmpty()) fail();
        }
    }


    @Test public void multi_linked(){
        int size = 16000+16000;
        fill(size(16000),100000, size(16000),200000);
        //TODO
    }



}