# Mobile Messaging SDK for Huawei on Android

[![Download](https://img.shields.io/github/v/tag/infobip/mobile-messaging-sdk-huawei?label=maven%20central)](https://mvnrepository.com/artifact/com.infobip/infobip-mobile-messaging-huawei-sdk)
[![License](https://img.shields.io/github/license/infobip/mobile-messaging-sdk-huawei.svg?label=License)](https://github.com/infobip/mobile-messaging-sdk-huawei/blob/master/LICENSE)

Mobile Messaging SDK is designed and developed to easily enable push notification channel in your mobile application. In almost no time of implementation you get push notification in your application and access to the features of <a href="https://www.infobip.com/en/products/mobile-app-messaging" target="_blank">Infobip Mobile Apps Messaging</a>. The document describes library integration steps. Additional information can be found in our <a href="https://github.com/infobip/mobile-messaging-sdk-huawei/wiki" target="_blank">Wiki</a>.

## Requirements

- Android Studio
- API Level: 21 (Android 5.0 - Lollipop) - 33 (Android 13.0 - Tiramisu)
- HMS Core (APK) 4.0.0.300 or later
- [AppGallery](https://huaweimobileservices.com/appgallery/)

## Quick start guide

1. Make sure to <a href="https://www.infobip.com/docs/mobile-app-messaging/getting-started#create-and-enable-a-mobile-application-profile" target="_blank">setup application at Infobip portal</a>, if you haven't already.
2. Add dependencies to `app/build.gradle`
    ```groovy
    dependencies {
        ...
        implementation ('com.infobip:infobip-mobile-messaging-huawei-sdk:6.7.0@aar') {
            transitive = true
        }
    }
    ```

3. <a href="https://developer.huawei.com/consumer/en/doc/development/HMSCore-Guides/android-config-agc-0000001050170137" target="_blank">`Configure Huawei application`</a> 
4. Add HMS App ID and Infobip <a href="https://www.infobip.com/docs/mobile-app-messaging/getting-started#copy-application-code-create-and-enable-a-mobile-application-profile" target="_blank">`Application Code`</a> obtained in step 1 to `values/strings.xml`
    ```groovy
    <resources>
        <string name="app_id">SENDER ID</string>
        <string name="infobip_application_code">APPLICATION CODE</string>
        ...
    </resources>
    ```
   HMS App ID could be taken from Huawei Developer Console - Application settings

5. Download `agconnect-services.json` from <a href="https://developer.huawei.com/consumer/ru/service/josp/agc/index.html"  target="_blank">AppGallery Connect </a> and copy it to your app's folder.
        
        a. Find your App from the list and click the link under Android App in the Mobile phone column.
        
        b. Go to Develop > Overview.
        
        c. In the App information area, Click `agconnect-services.json` to download the configuration file.
        
        **Note** that if you are developing / testing FCM and HMS at the same device then better to remove cache for installed app, remove app and after that install build with other push cloud. 
 
6. Add code to `MainActivity#onCreate`

    ```java
    public class MainActivity extends AppCompatActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            new MobileMessaging
                    .Builder(getApplication())
                    .build();
        }
    }
    ```
    <img src="https://github.com/infobip/mobile-messaging-sdk-android/wiki/images/QSGActivity.png?raw=true" alt="String resources"/>

> ### Notice:
> Since Android 13+ we are using default notification permission request dialog.
> It is possible to trigger permission request later, to learn how please follow: <a href="https://github.com/infobip/mobile-messaging-sdk-android/wiki/Android-13-Notification-Permission-Handling" target="_blank">Android 13 notification permission handling</a>.


<br>
<p align="center"><b>NEXT STEPS: <a href="https://github.com/infobip/mobile-messaging-sdk-android/wiki/User-profile">User profile</a></b></p>
<br>

> ### Notes
> 1. All required manifest components are merged to application manifest automatically by manifest merger. Please include <a href="https://github.com/infobip/mobile-messaging-sdk-huawei/wiki/Android-Manifest-components#push-notifications" target="_blank">push-related components</a> to manifest manually if manifest merger was disabled.
> 2. Keep in mind that some proprietary Android versions may restrict network traffic for your app. It may in turn affect delivery of push notifications.
> 3. 3.0.0-rc version doesn't contain geo module, it'll be updated in the next releases.
     <br>

| If you have any questions or suggestions, feel free to send an email to support@infobip.com or create an <a href="https://github.com/infobip/mobile-messaging-sdk-huawei/issues" target="_blank">issue</a>. |
|---|




