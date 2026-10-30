# Keep Native NDK Security Bridge and Auth Models
-keep class com.next.tv.launcher.security.NativeSecurityBridge { *; }
-keep class com.next.tv.launcher.security.NativeSecurityBridge$RequestAuthHeaders { *; }

# Keep Data Models parsed via reflection / background workers
-keep class com.next.tv.launcher.widget.WeatherDataManager$WeatherInfo { *; }
-keep class com.next.tv.launcher.widget.WeatherDataManager$DayForecast { *; }
-keep class com.next.tv.launcher.widget.NewsDataManager$NewsItem { *; }
-keepclassmembers class com.next.tv.launcher.widget.NewsDataManager$FeedSource { *; }
-keep class com.next.tv.launcher.widget.YouTubeTrendingManager$YouTubeVideoItem { *; }
-keep class com.next.tv.launcher.widget.FootballDataManager$FootballMatchItem { *; }
-keepclassmembers class com.next.tv.launcher.Config { *; }
-keepclassmembers class com.next.tv.launcher.model.Order { *; }
-keepclassmembers class com.next.tv.launcher.model.Cards { *; }
-keepclassmembers class com.next.tv.launcher.model.SectionConfig { *; }
-keepclassmembers class com.next.tv.launcher.model.Festival { *; }
-keep class com.next.tv.launcher.rec.TvRecommendationReader$MovieItem { *; }
-keep class com.next.tv.launcher.rec.TvRecommendationReader$MovieSection { *; }
-keep class com.next.tv.launcher.rec.TvRecommendationReader$Contract { *; }
-keep class com.next.tv.launcher.widget.MediaNotificationListener { *; }
-keep class com.next.tv.launcher.search.SearchResultItem { *; }
