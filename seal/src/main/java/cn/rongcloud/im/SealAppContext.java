package cn.rongcloud.im;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.alibaba.fastjson.JSONException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.rongcloud.im.db.Friend;
import cn.rongcloud.im.db.Groups;
import cn.rongcloud.im.message.module.SealExtensionModule;
import cn.rongcloud.im.server.broadcast.BroadcastManager;
import cn.rongcloud.im.server.network.http.HttpException;
import cn.rongcloud.im.server.pinyin.CharacterParser;
import cn.rongcloud.im.server.response.ContactNotificationMessageData;
import cn.rongcloud.im.server.utils.NLog;
import cn.rongcloud.im.server.utils.RongGenerate;
import cn.rongcloud.im.server.utils.json.JsonMananger;
import cn.rongcloud.im.ui.activity.MainActivity;
import cn.rongcloud.im.ui.activity.NewFriendListActivity;
import cn.rongcloud.im.ui.activity.UserDetailActivity;
import io.rong.imkit.DefaultExtensionModule;
import io.rong.imkit.IExtensionModule;
import io.rong.imkit.RongExtensionManager;
import io.rong.imkit.RongIM;
import io.rong.imkit.model.GroupNotificationMessageData;
import io.rong.imkit.model.GroupUserInfo;
import io.rong.imkit.model.UIConversation;
import io.rong.imkit.plugin.location.AMapPreviewActivity;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Group;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import io.rong.imlib.model.UserInfo;
import io.rong.message.ContactNotificationMessage;
import io.rong.message.GroupNotificationMessage;
import io.rong.message.ImageMessage;
import io.rong.message.LocationMessage;

/**
 * 融云相关监听 事件集合类
 * Created by AMing on 16/1/7.
 * Company RongCloud
 */
public class SealAppContext implements RongIM.ConversationListBehaviorListener,
    RongIMClient.OnReceiveMessageListener,
    RongIM.UserInfoProvider,
    RongIM.GroupInfoProvider,
    RongIM.GroupUserInfoProvider,
    RongIM.LocationProvider,
    RongIM.ConversationBehaviorListener {

    private static final int CLICK_CONVERSATION_USER_PORTRAIT = 1;


    private final static String TAG = "SealAppContext";
    public static final String UPDATE_FRIEND = "update_friend";
    public static final String UPDATE_RED_DOT = "update_red_dot";
    public static final String UPDATE_GROUP_NAME = "update_group_name";
    public static final String UPDATE_GROUP_MEMBER = "update_group_member";
    public static final String GROUP_DISMISS = "group_dismiss";

    private Context mContext;

    private static SealAppContext mRongCloudInstance;

    private LocationCallback mLastLocationCallback;

    private static ArrayList<Activity> mActivities;

    public SealAppContext(Context mContext) {
        this.mContext = mContext;
        initListener();
        mActivities = new ArrayList<>();
        SealUserInfoManager.init(mContext);
    }

    /**
     * 初始化 RongCloud.
     *
     * @param context 上下文。
     */
    public static void init(Context context) {

        if (mRongCloudInstance == null) {
            synchronized (SealAppContext.class) {

                if (mRongCloudInstance == null) {
                    mRongCloudInstance = new SealAppContext(context);
                }
            }
        }

    }

    /**
     * 获取RongCloud 实例。
     *
     * @return RongCloud。
     */
    public static SealAppContext getInstance() {
        return mRongCloudInstance;
    }

    /**
     * init 后就能设置的监听
     */
    private void initListener() {
        RongIM.setConversationBehaviorListener(this);//设置会话界面操作的监听器。
        RongIM.setConversationListBehaviorListener(this);
        RongIM.setUserInfoProvider(this, true);
        RongIM.setGroupInfoProvider(this, true);
        RongIM.setLocationProvider(this);//设置地理位置提供者,不用位置的同学可以注掉此行代码
        setInputProvider();
        setUserInfoEngineListener();
        setReadReceiptConversationType();
        RongIM.getInstance().enableNewComingMessageIcon(true);
        RongIM.getInstance().enableUnreadMessageIcon(true);
//        RongIM.setGroupUserInfoProvider(this, true);
    }

    private void setReadReceiptConversationType() {
        Conversation.ConversationType[] types = new Conversation.ConversationType[] {
            Conversation.ConversationType.PRIVATE,
            Conversation.ConversationType.GROUP,
            Conversation.ConversationType.DISCUSSION
        };
        RongIM.getInstance().setReadReceiptConversationTypeList(types);
    }

    private void setInputProvider() {
        RongIM.setOnReceiveMessageListener(this);

        List<IExtensionModule> moduleList = RongExtensionManager.getInstance().getExtensionModules();
        IExtensionModule defaultModule = null;
        if (moduleList != null) {
            for (IExtensionModule module : moduleList) {
                if (module instanceof DefaultExtensionModule) {
                    defaultModule = module;
                    break;
                }
            }
            if (defaultModule != null) {
                RongExtensionManager.getInstance().unregisterExtensionModule(defaultModule);
                RongExtensionManager.getInstance().registerExtensionModule(new SealExtensionModule());
            }
        }
    }

    /**
     * 需要 rongcloud connect 成功后设置的 listener
     */
    public void setUserInfoEngineListener() {
        UserInfoEngine.getInstance(mContext).setListener(new UserInfoEngine.UserInfoListener() {
            @Override
            public void onResult(UserInfo info) {
                if (info != null && RongIM.getInstance() != null) {
                    if (TextUtils.isEmpty(String.valueOf(info.getPortraitUri()))) {
                        info.setPortraitUri(Uri.parse(RongGenerate.generateDefaultAvatar(info.getName(), info.getUserId())));
                    }
                    NLog.e("UserInfoEngine", info.getName() + info.getPortraitUri());
                    RongIM.getInstance().refreshUserInfoCache(info);
                }
            }
        });
        GroupInfoEngine.getInstance(mContext).setmListener(new GroupInfoEngine.GroupInfoListeners() {
            @Override
            public void onResult(Group info) {
                if (info != null && RongIM.getInstance() != null) {
                    NLog.e("GroupInfoEngine:" + info.getId() + "----" + info.getName() + "----" + info.getPortraitUri());
                    if (TextUtils.isEmpty(String.valueOf(info.getPortraitUri()))) {
                        info.setPortraitUri(Uri.parse(RongGenerate.generateDefaultAvatar(info.getName(), info.getId())));
                    }
                    RongIM.getInstance().refreshGroupInfoCache(info);
                }
            }
        });
    }

    @Override
    public boolean onConversationPortraitClick(Context context, Conversation.ConversationType conversationType, String s) {
        return false;
    }

    @Override
    public boolean onConversationPortraitLongClick(Context context, Conversation.ConversationType conversationType, String s) {
        return false;
    }

    @Override
    public boolean onConversationLongClick(Context context, View view, UIConversation uiConversation) {
        return false;
    }

    @Override
    public boolean onConversationClick(Context context, View view, UIConversation uiConversation) {
        MessageContent messageContent = uiConversation.getMessageContent();
        if (messageContent instanceof ContactNotificationMessage) {
            ContactNotificationMessage contactNotificationMessage = (ContactNotificationMessage) messageContent;
            if (contactNotificationMessage.getOperation().equals("AcceptResponse")) {
                // 被加方同意请求后
                if (contactNotificationMessage.getExtra() != null) {
                    ContactNotificationMessageData bean = null;
                    try {
                        bean = JsonMananger.jsonToBean(contactNotificationMessage.getExtra(), ContactNotificationMessageData.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    RongIM.getInstance().startPrivateChat(context, uiConversation.getConversationSenderId(), bean.getSourceUserNickname());

                }
            } else {
                context.startActivity(new Intent(context, NewFriendListActivity.class));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onReceived(Message message, int i) {
        MessageContent messageContent = message.getContent();
        if (messageContent instanceof ContactNotificationMessage) {
            ContactNotificationMessage contactNotificationMessage = (ContactNotificationMessage) messageContent;
            if (contactNotificationMessage.getOperation().equals("Request")) {
                //对方发来好友邀请
                BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_RED_DOT);
            } else if (contactNotificationMessage.getOperation().equals("AcceptResponse")) {
                //对方同意我的好友请求
                ContactNotificationMessageData c = null;
                try {
                    c = JsonMananger.jsonToBean(contactNotificationMessage.getExtra(), ContactNotificationMessageData.class);
                } catch (HttpException e) {
                    e.printStackTrace();
                    return false;
                } catch (JSONException e) {
                    e.printStackTrace();
                    return false;
                }
                if (c != null) {
                    if (SealUserInfoManager.getInstance().isFriendsRelationship(contactNotificationMessage.getSourceUserId())) {
                        return false;
                    }
                    SealUserInfoManager.getInstance().addFriend(
                        new Friend(contactNotificationMessage.getSourceUserId(),
                                   c.getSourceUserNickname(),
                                   null, null, null, null,
                                   null, null,
                                   CharacterParser.getInstance().getSpelling(c.getSourceUserNickname()),
                                   null));
                }
                BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_FRIEND);
                BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_RED_DOT);
            }
//                // 发广播通知更新好友列表
//            BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_RED_DOT);
//            }
        } else if (messageContent instanceof GroupNotificationMessage) {
            GroupNotificationMessage groupNotificationMessage = (GroupNotificationMessage) messageContent;
            NLog.e("onReceived:" + groupNotificationMessage.getMessage());
            String groupID = message.getTargetId();
            GroupNotificationMessageData data = null;
            try {
                String currentID = RongIM.getInstance().getCurrentUserId();
                try {
                    data = jsonToBean(groupNotificationMessage.getData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (groupNotificationMessage.getOperation().equals("Create")) {
                    SealUserInfoManager.getInstance().getGroups(groupID);
                    SealUserInfoManager.getInstance().getGroupMember(groupID);
                } else if (groupNotificationMessage.getOperation().equals("Dismiss")) {
                    handleGroupDismiss(groupID);
                } else if (groupNotificationMessage.getOperation().equals("Kicked")) {
                    if (data != null) {
                        List<String> memberIdList = data.getTargetUserIds();
                        if (memberIdList != null) {
                            for (String userId : memberIdList) {
                                if (currentID.equals(userId)) {
                                    RongIM.getInstance().removeConversation(Conversation.ConversationType.GROUP, message.getTargetId(), new RongIMClient.ResultCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean aBoolean) {
                                            Log.e("SealAppContext", "Conversation remove successfully.");
                                        }

                                        @Override
                                        public void onError(RongIMClient.ErrorCode e) {

                                        }
                                    });
                                }
                            }
                        }

                        List<String> kickedUserIDs = data.getTargetUserIds();
                        SealUserInfoManager.getInstance().deleteGroupMembers(groupID, kickedUserIDs);
                        BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_GROUP_MEMBER, groupID);
                    }
                } else if (groupNotificationMessage.getOperation().equals("Add")) {
                    SealUserInfoManager.getInstance().getGroupMember(groupID);
                    BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_GROUP_MEMBER, groupID);
                } else if (groupNotificationMessage.getOperation().equals("Quit")) {
                    if (data != null) {
                        List<String> quitUserIDs = data.getTargetUserIds();
                        SealUserInfoManager.getInstance().deleteGroupMembers(groupID, quitUserIDs);
                        BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_GROUP_MEMBER, groupID);
                    }
                } else if (groupNotificationMessage.getOperation().equals("Rename")) {
                    if (data != null) {
                        String targetGroupName = data.getTargetGroupName();
                        SealUserInfoManager.getInstance().updateGroupsName(groupID, targetGroupName);
                        List<String> groupNameList = new ArrayList<>();
                        groupNameList.add(groupID);
                        groupNameList.add(data.getTargetGroupName());
                        groupNameList.add(data.getOperatorNickname());
                        BroadcastManager.getInstance(mContext).sendBroadcast(UPDATE_GROUP_NAME, groupNameList);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        } else if (messageContent instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage) messageContent;
        }
        return false;
    }

    private void handleGroupDismiss(final String groupID) {
        RongIM.getInstance().getConversation(Conversation.ConversationType.GROUP, groupID, new RongIMClient.ResultCallback<Conversation>() {
            @Override
            public void onSuccess(Conversation conversation) {
                RongIM.getInstance().clearMessages(Conversation.ConversationType.GROUP, groupID, new RongIMClient.ResultCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean aBoolean) {
                        RongIM.getInstance().removeConversation(Conversation.ConversationType.GROUP, groupID, null);
                    }

                    @Override
                    public void onError(RongIMClient.ErrorCode e) {

                    }
                });
            }

            @Override
            public void onError(RongIMClient.ErrorCode e) {

            }
        });
        SealUserInfoManager.getInstance().deleteGroups(new Groups(groupID));
        SealUserInfoManager.getInstance().deleteGroupMembers(groupID);
        BroadcastManager.getInstance(mContext).sendBroadcast(SealConst.GROUP_LIST_UPDATE);
        BroadcastManager.getInstance(mContext).sendBroadcast(GROUP_DISMISS, groupID);
    }

    @Override
    public UserInfo getUserInfo(String s) {
        UserInfoEngine.getInstance(mContext).startEngine(s);
        return null;
    }

    @Override
    public Group getGroupInfo(String s) {
        return GroupInfoEngine.getInstance(mContext).startEngine(s);
    }

    @Override
    public GroupUserInfo getGroupUserInfo(String groupId, String userId) {
//        return GroupUserInfoEngine.getInstance(mContext).startEngine(groupId, userId);
        return null;
    }


    @Override
    public void onStartLocation(Context context, LocationCallback locationCallback) {
        /**
         * demo 代码  开发者需替换成自己的代码。
         */
    }

    @Override
    public boolean onUserPortraitClick(Context context, Conversation.ConversationType conversationType, UserInfo userInfo) {
        if (conversationType == Conversation.ConversationType.CUSTOMER_SERVICE || conversationType == Conversation.ConversationType.PUBLIC_SERVICE || conversationType == Conversation.ConversationType.APP_PUBLIC_SERVICE) {
            return false;
        }
        //开发测试时,发送系统消息的userInfo只有id不为空
        if (userInfo != null && userInfo.getName() != null && userInfo.getPortraitUri() != null) {
            Intent intent = new Intent(context, UserDetailActivity.class);
            intent.putExtra("conversationType", conversationType.getValue());
            Friend friend = CharacterParser.getInstance().generateFriendFromUserInfo(userInfo);
            intent.putExtra("friend", friend);
            intent.putExtra("type", CLICK_CONVERSATION_USER_PORTRAIT);
            context.startActivity(intent);
        }
        return true;
    }

    @Override
    public boolean onUserPortraitLongClick(Context context, Conversation.ConversationType conversationType, UserInfo userInfo) {
        return false;
    }

    @Override
    public boolean onMessageClick(final Context context, final View view, final Message message) {
        //real-time location message end
        /**
         * demo 代码  开发者需替换成自己的代码。
         */
        if (message.getContent() instanceof LocationMessage) {
            Intent intent = new Intent(context, AMapPreviewActivity.class);
            intent.putExtra("location", message.getContent());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else if (message.getContent() instanceof ImageMessage) {
//            Intent intent = new Intent(context, PhotoActivity.class);
//            intent.putExtra("message", message);
//            context.startActivity(intent);
        }

        return false;
    }


    private void startRealTimeLocation(Context context, Conversation.ConversationType conversationType, String targetId) {

    }

    private void joinRealTimeLocation(Context context, Conversation.ConversationType conversationType, String targetId) {

    }

    @Override
    public boolean onMessageLinkClick(Context context, String s) {
        return false;
    }

    @Override
    public boolean onMessageLongClick(Context context, View view, Message message) {
        return false;
    }


    public LocationCallback getLastLocationCallback() {
        return mLastLocationCallback;
    }

    public void setLastLocationCallback(LocationCallback lastLocationCallback) {
        this.mLastLocationCallback = lastLocationCallback;
    }

    public void pushActivity(Activity activity) {
        mActivities.add(activity);
    }

    public void popActivity(Activity activity) {
        if (mActivities.contains(activity)) {
            activity.finish();
            mActivities.remove(activity);
        }
    }

    public void popAllActivity() {
        try {
            if (MainActivity.mViewPager != null) {
                MainActivity.mViewPager.setCurrentItem(0);
            }
            for (Activity activity : mActivities) {
                if (activity != null) {
                    activity.finish();
                }
            }
            mActivities.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RongIMClient.ConnectCallback getConnectCallback() {
        RongIMClient.ConnectCallback connectCallback = new RongIMClient.ConnectCallback() {
            @Override
            public void onTokenIncorrect() {
                NLog.d(TAG, "ConnectCallback connect onTokenIncorrect");
                SealUserInfoManager.getInstance().reGetToken();
            }

            @Override
            public void onSuccess(String s) {
                NLog.d(TAG, "ConnectCallback connect onSuccess");
                SharedPreferences sp = mContext.getSharedPreferences("config", Context.MODE_PRIVATE);
                sp.edit().putString(SealConst.SEALTALK_LOGIN_ID, s).apply();
            }

            @Override
            public void onError(final RongIMClient.ErrorCode e) {
                NLog.d(TAG, "ConnectCallback connect onError-ErrorCode=" + e);
            }
        };
        return connectCallback;
    }

    private GroupNotificationMessageData jsonToBean(String data) {
        GroupNotificationMessageData dataEntity = new GroupNotificationMessageData();
        try {
            JSONObject jsonObject = new JSONObject(data);
            if (jsonObject.has("operatorNickname")) {
                dataEntity.setOperatorNickname(jsonObject.getString("operatorNickname"));
            }
            if (jsonObject.has("targetGroupName")) {
                dataEntity.setTargetGroupName(jsonObject.getString("targetGroupName"));
            }
            if (jsonObject.has("timestamp")) {
                dataEntity.setTimestamp(jsonObject.getLong("timestamp"));
            }
            if (jsonObject.has("targetUserIds")) {
                JSONArray jsonArray = jsonObject.getJSONArray("targetUserIds");
                for (int i = 0; i < jsonArray.length(); i++) {
                    dataEntity.getTargetUserIds().add(jsonArray.getString(i));
                }
            }
            if (jsonObject.has("targetUserDisplayNames")) {
                JSONArray jsonArray = jsonObject.getJSONArray("targetUserDisplayNames");
                for (int i = 0; i < jsonArray.length(); i++) {
                    dataEntity.getTargetUserDisplayNames().add(jsonArray.getString(i));
                }
            }
            if (jsonObject.has("oldCreatorId")) {
                dataEntity.setOldCreatorId(jsonObject.getString("oldCreatorId"));
            }
            if (jsonObject.has("oldCreatorName")) {
                dataEntity.setOldCreatorName(jsonObject.getString("oldCreatorName"));
            }
            if (jsonObject.has("newCreatorId")) {
                dataEntity.setNewCreatorId(jsonObject.getString("newCreatorId"));
            }
            if (jsonObject.has("newCreatorName")) {
                dataEntity.setNewCreatorName(jsonObject.getString("newCreatorName"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataEntity;
    }
}
