package org.eclipse.jgit.internal.storage.replication;

import org.eclipse.jgit.internal.replication.SimpleObjectIdTombstone;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class SimpleObjectIdTombstoneTest {

    @Test
    public void testTombstoneWrapAround()
    {
        SimpleObjectIdTombstone obj = new SimpleObjectIdTombstone(4);
        obj.add(new ObjectId( 0, 0, 0, 0, 4));
        obj.add(new ObjectId( 0, 0, 0, 0, 5));
        obj.add(new ObjectId( 0, 0, 0, 0, 6));
        obj.add(new ObjectId( 0, 0, 0, 0, 7));
        Assert.assertEquals(obj.peekHead(), new ObjectId( 0, 0, 0, 0, 7));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 4)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 5)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 7)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 6)));

        obj.add(new ObjectId( 0, 0, 0, 0, 4));
        Assert.assertEquals(obj.peekHead(), new ObjectId( 0, 0, 0, 0, 4));

        obj.add(new ObjectId( 0, 0, 0, 0, 5));
        Assert.assertEquals(obj.peekHead(), new ObjectId( 0, 0, 0, 0, 5));

        // must contain only 4 objects, matching 5, 4, 7, 6 in that order.
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 5)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 4)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 6)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 7)));

        // add should bump each of these in order, so 6 goes first.
        obj.add(new ObjectId( 0, 0, 0, 0, 8));
        Assert.assertEquals(new ObjectId( 0, 0, 0, 0, 8), obj.peekHead());
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 4)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 5)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 7)));
        /** 6 should be bumped off the list. */
        Assert.assertFalse(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 6)));


        /** use add all to add 2 items at the same time, bumping 7, 4, off the list, 10 should
         * then be head.
         */
        obj.addAll(Arrays.asList(new ObjectId( 0, 0, 0, 0, 9), new ObjectId(0, 0, 0, 0, 10)));
        Assert.assertEquals(new ObjectId( 0, 0, 0, 0, 10), obj.peekHead());

        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 10)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 9)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 8)));
        Assert.assertTrue(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 5)));
        Assert.assertFalse(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 4)));
        Assert.assertFalse(obj.containsPeek(new ObjectId( 0, 0, 0, 0, 7)));

    }


    @Test
    public void checkTombstoneLoaderOnStartupWithNothingIsEmpty(){
        // check loading with empty does nothing.
        SimpleObjectIdTombstone obj = new SimpleObjectIdTombstone(4);
        Assert.assertEquals(null, obj.peekHead());
    }

    @Test
    public void checkTombstoneLoaderOnStartupWithRealStringObjectIds(){
        // note 2 is last so should be head.
        Collection<ObjectId> tombstoneIds = Arrays.asList(
                new ObjectId(0, 0, 0, 0, 3),
                new ObjectId(0, 0, 0, 0, 1),
                new ObjectId(0, 0, 0, 0, 2));

        // turn to comma seperate string list... this means we have the real object ids above
        // to check results still.
        final StringBuilder tombstoneCommaSeperated = generateCommaSeperatedList(tombstoneIds);

        SimpleObjectIdTombstone obj = new SimpleObjectIdTombstone(4, tombstoneCommaSeperated.toString());
        Assert.assertEquals(new ObjectId(0, 0, 0, 0, 2), obj.peekHead());
    }

    private static StringBuilder generateCommaSeperatedList(Collection<ObjectId> tombstoneIds) {
        final StringBuilder tombstoneCommaSeperated = new StringBuilder();
        for ( ObjectId id : tombstoneIds){
            if( tombstoneCommaSeperated.length() > 0 ) {
                tombstoneCommaSeperated.append(", ");
            }
            tombstoneCommaSeperated.append(id.name());
        }
        return tombstoneCommaSeperated;
    }

    @Test
    public void checkTombstoneLoaderOnStartupWithInvalidStringObjectIds(){

        SimpleObjectIdTombstone obj = new SimpleObjectIdTombstone(4,
                "Bob, The, Builder");
        Assert.assertEquals(null, obj.peekHead());
    }
}
