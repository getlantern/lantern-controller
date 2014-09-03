package org.lantern.friending;

import static org.junit.Assert.*;

import org.junit.Test;

public class FriendingTest {
    @Test
    public void testMaxFriendsForDegree() {
        assertEquals(30, Friending.maxFriendsForDegree(0));
        assertEquals(18, Friending.maxFriendsForDegree(1));
        assertEquals(13, Friending.maxFriendsForDegree(2));
        assertEquals(10, Friending.maxFriendsForDegree(3));
        assertEquals(8, Friending.maxFriendsForDegree(4));
        assertEquals(6, Friending.maxFriendsForDegree(5));
        assertEquals(6, Friending.maxFriendsForDegree(6));
        assertEquals(6, Friending.maxFriendsForDegree(7));
        assertEquals(6, Friending.maxFriendsForDegree(8));
        assertEquals(6, Friending.maxFriendsForDegree(9));
        assertEquals(6, Friending.maxFriendsForDegree(10));
        assertEquals(6, Friending.maxFriendsForDegree(11));
        assertEquals(6, Friending.maxFriendsForDegree(12));
        assertEquals(6, Friending.maxFriendsForDegree(13));
        assertEquals(6, Friending.maxFriendsForDegree(14));
        assertEquals(6, Friending.maxFriendsForDegree(15));
        assertEquals(6, Friending.maxFriendsForDegree(16));
        assertEquals(6, Friending.maxFriendsForDegree(17));
        assertEquals(6, Friending.maxFriendsForDegree(18));
        assertEquals(6, Friending.maxFriendsForDegree(19));
    }

}
