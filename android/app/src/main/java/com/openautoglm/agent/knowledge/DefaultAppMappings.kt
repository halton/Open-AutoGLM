package com.openautoglm.agent.knowledge

import com.openautoglm.agent.data.entities.AppCategory
import com.openautoglm.agent.data.entities.AppMapping

/**
 * Pre-defined app mappings for the VLM Android Agent.
 *
 * Contains default mappings for common Chinese and Western apps,
 * organized by category with localized prompts for each application.
 */
object DefaultAppMappings {

    /**
     * List of default app mappings organized by category.
     * These mappings provide the VLM agent with knowledge about
     * app names, aliases, and how to interact with them.
     */
    val apps: List<AppMapping> = buildList {
        // ============================================
        // SOCIAL & MESSAGING APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.tencent.mm",
                appName = "WeChat",
                category = AppCategory.SOCIAL,
                zhPrompt = "微信是一款社交通讯应用，支持聊天、朋友圈、支付等功能",
                enPrompt = "WeChat is a social messaging app supporting chat, moments, and payments",
                aliases = listOf("微信", "wechat", "Weixin")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.tencent.mobileqq",
                appName = "QQ",
                category = AppCategory.SOCIAL,
                zhPrompt = "QQ是腾讯的即时通讯应用，支持聊天、空间、游戏等功能",
                enPrompt = "QQ is Tencent's instant messaging app with chat, space, and games",
                aliases = listOf("腾讯QQ", "qq", "TencentQQ")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.sina.weibo",
                appName = "Weibo",
                category = AppCategory.SOCIAL,
                zhPrompt = "微博是中国最大的社交媒体平台，用于分享动态和关注热点",
                enPrompt = "Weibo is China's largest social media platform for sharing updates",
                aliases = listOf("微博", "新浪微博", "Sina Weibo")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                category = AppCategory.SOCIAL,
                zhPrompt = "WhatsApp是国际流行的即时通讯应用",
                enPrompt = "WhatsApp is a popular international messaging app",
                aliases = listOf("whatsapp", "Whatsapp")
            )
        )
        add(
            AppMapping.create(
                packageName = "org.telegram.messenger",
                appName = "Telegram",
                category = AppCategory.SOCIAL,
                zhPrompt = "Telegram是注重隐私的即时通讯应用",
                enPrompt = "Telegram is a privacy-focused instant messaging app",
                aliases = listOf("telegram", "电报")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.twitter.android",
                appName = "X",
                category = AppCategory.SOCIAL,
                zhPrompt = "X（原Twitter）是全球社交媒体平台",
                enPrompt = "X (formerly Twitter) is a global social media platform",
                aliases = listOf("Twitter", "twitter", "推特")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.xingin.xhs",
                appName = "Xiaohongshu",
                category = AppCategory.SOCIAL,
                zhPrompt = "小红书是生活方式分享平台，以种草笔记著称",
                enPrompt = "Xiaohongshu (RED) is a lifestyle sharing platform known for reviews",
                aliases = listOf("小红书", "RED", "Little Red Book")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.zhihu.android",
                appName = "Zhihu",
                category = AppCategory.SOCIAL,
                zhPrompt = "知乎是中文问答社区，分享知识和见解",
                enPrompt = "Zhihu is a Chinese Q&A community for sharing knowledge",
                aliases = listOf("知乎")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.douban.frodo",
                appName = "Douban",
                category = AppCategory.SOCIAL,
                zhPrompt = "豆瓣是文艺社区，提供书影音评分和小组讨论",
                enPrompt = "Douban is a cultural community for books, movies, and music reviews",
                aliases = listOf("豆瓣")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.reddit.frontpage",
                appName = "Reddit",
                category = AppCategory.SOCIAL,
                zhPrompt = "Reddit是国际社区论坛平台",
                enPrompt = "Reddit is an international community forum platform",
                aliases = listOf("reddit")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.quora.android",
                appName = "Quora",
                category = AppCategory.SOCIAL,
                zhPrompt = "Quora是英文问答社区平台",
                enPrompt = "Quora is an English Q&A community platform",
                aliases = listOf("quora")
            )
        )

        // ============================================
        // E-COMMERCE APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.taobao.taobao",
                appName = "Taobao",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "淘宝是阿里巴巴旗下的综合电商平台，支持购物和闪购",
                enPrompt = "Taobao is Alibaba's comprehensive e-commerce platform",
                aliases = listOf("淘宝", "淘宝闪购", "taobao")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.jingdong.app.mall",
                appName = "JD",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "京东是中国领先的自营电商平台，以物流快速著称",
                enPrompt = "JD.com is a leading Chinese e-commerce platform known for fast delivery",
                aliases = listOf("京东", "京东秒送", "JD.com", "jingdong")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.xunmeng.pinduoduo",
                appName = "Pinduoduo",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "拼多多是社交电商平台，以团购低价著称",
                enPrompt = "Pinduoduo is a social e-commerce platform known for group buying",
                aliases = listOf("拼多多", "PDD")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.einnovation.temu",
                appName = "Temu",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "Temu是拼多多旗下的跨境电商平台",
                enPrompt = "Temu is Pinduoduo's cross-border e-commerce platform",
                aliases = listOf("temu")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.amazon.mShop.android.shopping",
                appName = "Amazon",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "亚马逊是全球领先的跨境电商平台，提供海量商品和Prime会员服务",
                enPrompt = "Amazon is a global e-commerce platform with vast product selection and Prime membership",
                aliases = listOf("亚马逊", "amazon", "Amazon Shopping")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.contextlogic.wish",
                appName = "Wish",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "Wish是国际折扣购物平台",
                enPrompt = "Wish is an international discount shopping platform",
                aliases = listOf("wish")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.alibaba.intl.android.apps.poseidon",
                appName = "AliExpress",
                category = AppCategory.ECOMMERCE,
                zhPrompt = "速卖通(AliExpress)是阿里巴巴国际零售平台",
                enPrompt = "AliExpress is Alibaba's international retail platform",
                aliases = listOf("速卖通", "aliexpress", "AliExpress")
            )
        )

        // ============================================
        // TRAVEL & TRANSPORTATION APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.MobileTicket",
                appName = "12306",
                category = AppCategory.TRAVEL,
                zhPrompt = "铁路12306是中国铁路官方购票平台",
                enPrompt = "12306 is China Railway's official ticket booking platform",
                aliases = listOf("铁路12306", "12306", "Railway 12306")
            )
        )
        add(
            AppMapping.create(
                packageName = "ctrip.android.view",
                appName = "Ctrip",
                category = AppCategory.TRAVEL,
                zhPrompt = "携程是综合旅游服务平台，提供机票、酒店、旅游预订",
                enPrompt = "Ctrip is a comprehensive travel service platform",
                aliases = listOf("携程", "携程旅行", "Trip.com")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.Qunar",
                appName = "Qunar",
                category = AppCategory.TRAVEL,
                zhPrompt = "去哪儿是旅游搜索比价平台",
                enPrompt = "Qunar is a travel search and price comparison platform",
                aliases = listOf("去哪儿", "去哪儿旅行")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.booking",
                appName = "Booking.com",
                category = AppCategory.TRAVEL,
                zhPrompt = "Booking.com是国际酒店预订平台",
                enPrompt = "Booking.com is an international hotel booking platform",
                aliases = listOf("Booking", "booking", "booking.com", "BOOKING.COM")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.expedia.bookings",
                appName = "Expedia",
                category = AppCategory.TRAVEL,
                zhPrompt = "Expedia是国际旅游预订平台",
                enPrompt = "Expedia is an international travel booking platform",
                aliases = listOf("expedia")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.sdu.did.psnger",
                appName = "Didi",
                category = AppCategory.TRAVEL,
                zhPrompt = "滴滴出行是中国最大的网约车平台",
                enPrompt = "Didi is China's largest ride-hailing platform",
                aliases = listOf("滴滴出行", "滴滴", "DiDi")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.autonavi.minimap",
                appName = "Amap",
                category = AppCategory.TRAVEL,
                zhPrompt = "高德地图是中国领先的导航和地图应用",
                enPrompt = "Amap (Gaode) is China's leading navigation and map app",
                aliases = listOf("高德地图", "高德", "Gaode")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.baidu.BaiduMap",
                appName = "Baidu Map",
                category = AppCategory.TRAVEL,
                zhPrompt = "百度地图是百度旗下的地图导航应用",
                enPrompt = "Baidu Map is Baidu's map and navigation app",
                aliases = listOf("百度地图")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.maps",
                appName = "Google Maps",
                category = AppCategory.TRAVEL,
                zhPrompt = "谷歌地图是全球领先的地图导航服务",
                enPrompt = "Google Maps is the world's leading map and navigation service",
                aliases = listOf("GoogleMaps", "googlemaps", "google maps")
            )
        )

        // ============================================
        // FOOD & DELIVERY APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.sankuai.meituan",
                appName = "Meituan",
                category = AppCategory.FOOD,
                zhPrompt = "美团是本地生活服务平台，提供外卖、团购、酒店等服务",
                enPrompt = "Meituan is a local services platform for food delivery and more",
                aliases = listOf("美团", "美团外卖")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.dianping.v1",
                appName = "Dianping",
                category = AppCategory.FOOD,
                zhPrompt = "大众点评是中国领先的本地生活信息平台",
                enPrompt = "Dianping is China's leading local life information platform",
                aliases = listOf("大众点评", "点评")
            )
        )
        add(
            AppMapping.create(
                packageName = "me.ele",
                appName = "Eleme",
                category = AppCategory.FOOD,
                zhPrompt = "饿了么是阿里巴巴旗下的外卖配送平台",
                enPrompt = "Eleme is Alibaba's food delivery platform",
                aliases = listOf("饿了么", "ele.me")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.yek.android.kfc.activitys",
                appName = "KFC",
                category = AppCategory.FOOD,
                zhPrompt = "肯德基是国际快餐连锁品牌的官方应用",
                enPrompt = "KFC is the official app for the fast food restaurant chain",
                aliases = listOf("肯德基", "kfc")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.mcdonalds.app",
                appName = "McDonald's",
                category = AppCategory.FOOD,
                zhPrompt = "麦当劳是国际快餐连锁品牌的官方应用",
                enPrompt = "McDonald's is the official app for the fast food restaurant chain",
                aliases = listOf("McDonald", "mcdonald", "麦当劳")
            )
        )

        // ============================================
        // ENTERTAINMENT APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "tv.danmaku.bili",
                appName = "Bilibili",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "哔哩哔哩是中国领先的弹幕视频网站",
                enPrompt = "Bilibili is China's leading video platform with bullet comments",
                aliases = listOf("bilibili", "B站", "哔哩哔哩")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.ss.android.ugc.aweme",
                appName = "Douyin",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "抖音是中国版TikTok，短视频分享平台",
                enPrompt = "Douyin is the Chinese version of TikTok for short videos",
                aliases = listOf("抖音")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.zhiliaoapp.musically",
                appName = "TikTok",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "TikTok是国际版抖音，全球流行的短视频平台",
                enPrompt = "TikTok is the international short video platform",
                aliases = listOf("tiktok", "Tiktok")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.smile.gifmaker",
                appName = "Kuaishou",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "快手是中国短视频和直播平台",
                enPrompt = "Kuaishou is a Chinese short video and live streaming platform",
                aliases = listOf("快手")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.tencent.qqlive",
                appName = "Tencent Video",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "腾讯视频是腾讯旗下的视频流媒体平台",
                enPrompt = "Tencent Video is Tencent's video streaming platform",
                aliases = listOf("腾讯视频")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.qiyi.video",
                appName = "iQIYI",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "爱奇艺是百度旗下的视频流媒体平台",
                enPrompt = "iQIYI is Baidu's video streaming platform",
                aliases = listOf("爱奇艺", "iqiyi")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.youku.phone",
                appName = "Youku",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "优酷是阿里巴巴旗下的视频流媒体平台",
                enPrompt = "Youku is Alibaba's video streaming platform",
                aliases = listOf("优酷视频", "优酷")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.hunantv.imgo.activity",
                appName = "Mango TV",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "芒果TV是湖南卫视旗下的视频平台",
                enPrompt = "Mango TV is Hunan TV's video streaming platform",
                aliases = listOf("芒果TV")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.phoenix.read",
                appName = "Hongguo Short Drama",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "红果短剧是短剧视频平台",
                enPrompt = "Hongguo is a short drama video platform",
                aliases = listOf("红果短剧")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.netease.cloudmusic",
                appName = "NetEase Cloud Music",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "网易云音乐是中国领先的音乐流媒体平台",
                enPrompt = "NetEase Cloud Music is China's leading music streaming platform",
                aliases = listOf("网易云音乐", "网易云")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.tencent.qqmusic",
                appName = "QQ Music",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "QQ音乐是腾讯旗下的音乐流媒体平台",
                enPrompt = "QQ Music is Tencent's music streaming platform",
                aliases = listOf("QQ音乐")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.luna.music",
                appName = "Qishui Music",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "汽水音乐是字节跳动旗下的音乐应用",
                enPrompt = "Qishui Music is ByteDance's music streaming app",
                aliases = listOf("汽水音乐")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.ximalaya.ting.android",
                appName = "Ximalaya",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "喜马拉雅是中国领先的音频内容平台",
                enPrompt = "Ximalaya is China's leading audio content platform",
                aliases = listOf("喜马拉雅", "喜马拉雅FM")
            )
        )
        add(
            AppMapping.create(
                packageName = "org.videolan.vlc",
                appName = "VLC",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "VLC是开源的跨平台多媒体播放器",
                enPrompt = "VLC is an open-source cross-platform media player",
                aliases = listOf("vlc", "VLC Player")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.Project100Pi.themusicplayer",
                appName = "Pi Music Player",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "Pi Music Player是本地音乐播放器",
                enPrompt = "Pi Music Player is a local music player app",
                aliases = listOf("PiMusicPlayer", "pimusicplayer")
            )
        )
        add(
            AppMapping.create(
                packageName = "code.name.monkey.retromusic",
                appName = "Retro Music",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "Retro Music是开源的本地音乐播放器",
                enPrompt = "Retro Music is an open-source local music player",
                aliases = listOf("RetroMusic", "retromusic")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.dragon.read",
                appName = "Fanqie Novel",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "番茄小说是字节跳动旗下的免费小说阅读平台",
                enPrompt = "Fanqie Novel is ByteDance's free novel reading platform",
                aliases = listOf("番茄小说", "番茄免费小说")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.kmxs.reader",
                appName = "Qimao Novel",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "七猫免费小说是免费小说阅读平台",
                enPrompt = "Qimao is a free novel reading platform",
                aliases = listOf("七猫免费小说", "七猫小说")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.miHoYo.hkrpg",
                appName = "Honkai: Star Rail",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "崩坏：星穹铁道是米哈游开发的RPG游戏",
                enPrompt = "Honkai: Star Rail is an RPG game developed by miHoYo",
                aliases = listOf("星穹铁道", "崩坏：星穹铁道")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.papegames.lysk.cn",
                appName = "Love and Deepspace",
                category = AppCategory.ENTERTAINMENT,
                zhPrompt = "恋与深空是叠纸游戏开发的恋爱游戏",
                enPrompt = "Love and Deepspace is a romance game by Papergames",
                aliases = listOf("恋与深空")
            )
        )

        // ============================================
        // PRODUCTIVITY APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.ss.android.lark",
                appName = "Feishu",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "飞书是字节跳动旗下的企业协作平台",
                enPrompt = "Feishu (Lark) is ByteDance's enterprise collaboration platform",
                aliases = listOf("飞书", "Lark")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.tencent.androidqqmail",
                appName = "QQ Mail",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "QQ邮箱是腾讯旗下的邮件服务",
                enPrompt = "QQ Mail is Tencent's email service",
                aliases = listOf("QQ邮箱")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.gm",
                appName = "Gmail",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Gmail是谷歌的电子邮件服务",
                enPrompt = "Gmail is Google's email service",
                aliases = listOf("gmail", "GoogleMail", "Google Mail")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.docs",
                appName = "Google Drive",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Drive是谷歌的云存储服务",
                enPrompt = "Google Drive is Google's cloud storage service",
                aliases = listOf("GoogleDrive", "Googledrive", "googledrive", "google drive", "Google-Drive", "google-drive")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.docs.editors.docs",
                appName = "Google Docs",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Docs是谷歌的在线文档编辑器",
                enPrompt = "Google Docs is Google's online document editor",
                aliases = listOf("GoogleDocs", "googledocs", "google docs")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.docs.editors.slides",
                appName = "Google Slides",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Slides是谷歌的在线演示文稿工具",
                enPrompt = "Google Slides is Google's online presentation tool",
                aliases = listOf("GoogleSlides", "Google-Slides")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.calendar",
                appName = "Google Calendar",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Calendar是谷歌的日历应用",
                enPrompt = "Google Calendar is Google's calendar app",
                aliases = listOf("GoogleCalendar", "Google-Calendar", "google-calendar", "google calendar")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.tasks",
                appName = "Google Tasks",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Tasks是谷歌的任务管理应用",
                enPrompt = "Google Tasks is Google's task management app",
                aliases = listOf("GoogleTasks", "Google-Tasks")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.keep",
                appName = "Google Keep",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Keep是谷歌的便签和笔记应用",
                enPrompt = "Google Keep is Google's notes and lists app",
                aliases = listOf("GoogleKeep", "googlekeep")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.dynamite",
                appName = "Google Chat",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Google Chat是谷歌的企业通讯应用",
                enPrompt = "Google Chat is Google's enterprise messaging app",
                aliases = listOf("GoogleChat", "Google-Chat")
            )
        )
        add(
            AppMapping.create(
                packageName = "net.cozic.joplin",
                appName = "Joplin",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Joplin是开源的笔记和待办应用",
                enPrompt = "Joplin is an open-source note-taking and to-do app",
                aliases = listOf("joplin")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.duolingo",
                appName = "Duolingo",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "Duolingo是免费的语言学习应用",
                enPrompt = "Duolingo is a free language learning app",
                aliases = listOf("duolingo", "多邻国")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.larus.nova",
                appName = "Doubao",
                category = AppCategory.PRODUCTIVITY,
                zhPrompt = "豆包是字节跳动旗下的AI助手应用",
                enPrompt = "Doubao is ByteDance's AI assistant app",
                aliases = listOf("豆包")
            )
        )

        // ============================================
        // FINANCE APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.hexin.plat.android",
                appName = "Tonghuashun",
                category = AppCategory.FINANCE,
                zhPrompt = "同花顺是中国领先的股票交易和行情软件",
                enPrompt = "Tonghuashun is China's leading stock trading and market app",
                aliases = listOf("同花顺")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.rammigsoftware.bluecoins",
                appName = "Bluecoins",
                category = AppCategory.FINANCE,
                zhPrompt = "Bluecoins是个人财务管理应用",
                enPrompt = "Bluecoins is a personal finance management app",
                aliases = listOf("bluecoins")
            )
        )

        // ============================================
        // SYSTEM & UTILITY APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.android.settings",
                appName = "Settings",
                category = AppCategory.SYSTEM,
                zhPrompt = "系统设置应用，用于配置Android设备的各项参数",
                enPrompt = "System Settings app for configuring Android device parameters",
                aliases = listOf("AndroidSystemSettings", "Android System Settings", "Android-System-Settings", "设置")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.chrome",
                appName = "Chrome",
                category = AppCategory.SYSTEM,
                zhPrompt = "Chrome是谷歌的网页浏览器",
                enPrompt = "Chrome is Google's web browser",
                aliases = listOf("chrome", "Google Chrome", "谷歌浏览器")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.microsoft.emmx",
                appName = "Microsoft Edge",
                category = AppCategory.SYSTEM,
                zhPrompt = "Microsoft Edge是微软的网页浏览器",
                enPrompt = "Microsoft Edge is Microsoft's web browser",
                aliases = listOf("Edge", "edge")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.deskclock",
                appName = "Clock",
                category = AppCategory.SYSTEM,
                zhPrompt = "时钟应用，提供闹钟、计时器和秒表功能",
                enPrompt = "Clock app with alarm, timer, and stopwatch features",
                aliases = listOf("clock", "闹钟", "时钟")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.deskclock",
                appName = "Google Clock",
                category = AppCategory.SYSTEM,
                zhPrompt = "谷歌时钟应用，提供闹钟、计时器和秒表功能",
                enPrompt = "Google Clock app with alarm, timer, and stopwatch features",
                aliases = listOf("GoogleClock", "Google-Clock")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.contacts",
                appName = "Contacts",
                category = AppCategory.SYSTEM,
                zhPrompt = "联系人应用，管理通讯录",
                enPrompt = "Contacts app for managing your address book",
                aliases = listOf("contacts", "通讯录", "联系人")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.contacts",
                appName = "Google Contacts",
                category = AppCategory.SYSTEM,
                zhPrompt = "谷歌联系人应用，管理通讯录",
                enPrompt = "Google Contacts app for managing your address book",
                aliases = listOf("GoogleContacts", "Google-Contacts", "google-contacts", "google contacts")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.fileexplorer",
                appName = "Files",
                category = AppCategory.SYSTEM,
                zhPrompt = "文件管理器，浏览和管理设备文件",
                enPrompt = "File manager for browsing and managing device files",
                aliases = listOf("files", "File Manager", "file manager", "文件管理器")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.nbu.files",
                appName = "Files by Google",
                category = AppCategory.SYSTEM,
                zhPrompt = "谷歌文件管理器，浏览和管理设备文件",
                enPrompt = "Files by Google for browsing and managing device files",
                aliases = listOf("GoogleFiles", "googlefiles", "FilesbyGoogle")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.soundrecorder",
                appName = "Audio Recorder",
                category = AppCategory.SYSTEM,
                zhPrompt = "录音机应用，录制音频",
                enPrompt = "Audio Recorder app for recording audio",
                aliases = listOf("AudioRecorder", "audiorecorder", "录音机")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.android.vending",
                appName = "Google Play Store",
                category = AppCategory.SYSTEM,
                zhPrompt = "Google Play商店，下载Android应用和游戏",
                enPrompt = "Google Play Store for downloading Android apps and games",
                aliases = listOf("GooglePlayStore", "Google-Play-Store", "Play Store", "应用商店")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.books",
                appName = "Google Play Books",
                category = AppCategory.SYSTEM,
                zhPrompt = "Google Play图书，阅读电子书",
                enPrompt = "Google Play Books for reading ebooks",
                aliases = listOf("GooglePlayBooks", "googleplaybooks", "Google-Play-Books", "google-play-books", "google play books")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.simplemobiletools.smsmessenger",
                appName = "Simple SMS Messenger",
                category = AppCategory.SYSTEM,
                zhPrompt = "简洁的短信应用",
                enPrompt = "Simple SMS Messenger for text messaging",
                aliases = listOf("SimpleSMSMessenger", "SMS")
            )
        )
        add(
            AppMapping.create(
                packageName = "net.osmand",
                appName = "OsmAnd",
                category = AppCategory.SYSTEM,
                zhPrompt = "OsmAnd是开源的离线地图和导航应用",
                enPrompt = "OsmAnd is an open-source offline maps and navigation app",
                aliases = listOf("Osmand", "osmand")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.google.android.apps.fitness",
                appName = "Google Fit",
                category = AppCategory.SYSTEM,
                zhPrompt = "Google Fit是谷歌的健康追踪应用",
                enPrompt = "Google Fit is Google's health tracking app",
                aliases = listOf("GoogleFit", "googlefit")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.flauschcode.broccoli",
                appName = "Broccoli",
                category = AppCategory.SYSTEM,
                zhPrompt = "Broccoli是食谱管理应用",
                enPrompt = "Broccoli is a recipe management app",
                aliases = listOf("broccoli")
            )
        )

        // ============================================
        // HEALTH & FITNESS APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.gotokeep.keep",
                appName = "Keep",
                category = AppCategory.OTHER,
                zhPrompt = "Keep是中国领先的健身运动应用",
                enPrompt = "Keep is China's leading fitness and workout app",
                aliases = listOf("keep")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.lingan.seeyou",
                appName = "Meiyou",
                category = AppCategory.OTHER,
                zhPrompt = "美柚是女性健康管理应用",
                enPrompt = "Meiyou is a women's health management app",
                aliases = listOf("美柚")
            )
        )

        // ============================================
        // NEWS & INFORMATION APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.tencent.news",
                appName = "Tencent News",
                category = AppCategory.OTHER,
                zhPrompt = "腾讯新闻是腾讯旗下的新闻资讯平台",
                enPrompt = "Tencent News is Tencent's news and information platform",
                aliases = listOf("腾讯新闻")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.ss.android.article.news",
                appName = "Toutiao",
                category = AppCategory.OTHER,
                zhPrompt = "今日头条是字节跳动旗下的新闻资讯平台",
                enPrompt = "Toutiao is ByteDance's news and information platform",
                aliases = listOf("今日头条", "头条")
            )
        )

        // ============================================
        // REAL ESTATE APPS
        // ============================================
        add(
            AppMapping.create(
                packageName = "com.lianjia.beike",
                appName = "Beike",
                category = AppCategory.OTHER,
                zhPrompt = "贝壳找房是中国领先的房产交易平台",
                enPrompt = "Beike is China's leading real estate transaction platform",
                aliases = listOf("贝壳找房", "贝壳")
            )
        )
        add(
            AppMapping.create(
                packageName = "com.anjuke.android.app",
                appName = "Anjuke",
                category = AppCategory.OTHER,
                zhPrompt = "安居客是房产信息服务平台",
                enPrompt = "Anjuke is a real estate information service platform",
                aliases = listOf("安居客")
            )
        )
    }

    /**
     * Get an app mapping by package name.
     *
     * @param packageName The Android package name to look up
     * @return The AppMapping if found, null otherwise
     */
    fun getByPackageName(packageName: String): AppMapping? {
        return apps.find { it.packageName == packageName }
    }

    /**
     * Get an app mapping by app name or alias.
     *
     * @param name The app name or alias to search for
     * @return The AppMapping if found, null otherwise
     */
    fun getByNameOrAlias(name: String): AppMapping? {
        return apps.find { mapping ->
            mapping.appName.equals(name, ignoreCase = true) ||
                mapping.aliases.any { it.equals(name, ignoreCase = true) }
        }
    }

    /**
     * Get all app mappings for a specific category.
     *
     * @param category The category to filter by
     * @return List of AppMappings in the specified category
     */
    fun getByCategory(category: AppCategory): List<AppMapping> {
        return apps.filter { it.category == category }
    }

    /**
     * Get all package names for quick lookup.
     *
     * @return Set of all package names in the default mappings
     */
    fun getAllPackageNames(): Set<String> {
        return apps.map { it.packageName }.toSet()
    }

    /**
     * Search for apps matching a query string.
     * Searches in app name and aliases.
     *
     * @param query The search query
     * @return List of matching AppMappings
     */
    fun search(query: String): List<AppMapping> {
        val lowerQuery = query.lowercase()
        return apps.filter { mapping ->
            mapping.appName.lowercase().contains(lowerQuery) ||
                mapping.aliases.any { it.lowercase().contains(lowerQuery) }
        }
    }
}
