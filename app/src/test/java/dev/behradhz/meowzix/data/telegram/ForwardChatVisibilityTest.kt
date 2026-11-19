package dev.behradhz.meowzix.data.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardChatVisibilityTest {
    @Test fun proxySponsoredChatsAreNeverForwardDestinations() {
        val sponsoredPosition = TdApi.ChatPosition().apply {
            source = TdApi.ChatSourceMtprotoProxy()
        }
        assertTrue(isMtprotoProxySponsoredForwardChat(arrayOf(sponsoredPosition)))
        assertFalse(isMtprotoProxySponsoredForwardChat(emptyArray()))
    }

    @Test fun locallyCachedFolderMembershipUsesChatLists() {
        val folderId = 11
        assertTrue(isChatInForwardFolder(arrayOf(TdApi.ChatListFolder(folderId)), folderId))
        assertFalse(isChatInForwardFolder(arrayOf(TdApi.ChatListFolder(12)), folderId))
        assertFalse(isChatInForwardFolder(emptyArray(), folderId))
    }
}
