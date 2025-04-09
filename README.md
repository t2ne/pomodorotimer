# 📱 Android Pomodoro Timer

A feature-rich Pomodoro timer application developed for Android using Kotlin. This app helps users maintain focus and track their productivity using the Pomodoro Technique.

## 🌟 Features

- **Smart Timer**: Customizable Pomodoro sessions with circular progress indicator
- **Motion Detection**: Uses accelerometer to detect phone movement and pause sessions
- **Progress Tracking**: Comprehensive history view with daily statistics
- **Visual Analytics**: Bar charts to visualize your productivity patterns
- **Clean UI**: Modern, intuitive interface with smooth navigation
- **Local Storage**: Persistent data storage using Room database
- **Smart Notifications**: Get alerted when your focus is interrupted

## 🛠️ Installation

### Clone the Repository

```bash
git clone https://github.com/t2ne/pomodoro-timer.git
```

### Open the Project

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run the application

### Requirements

- Android Studio Arctic Fox or newer
- Minimum SDK: Android 21 (Lollipop)
- Target SDK: Android 34
- Kotlin version: 1.9.0 or newer

## 📂 Project Structure

- **app**: Main application module
  - **src/main**
    - **java/com/example/pomodoro**
      - `MainActivity.kt`: Main application entry point
      - `SplashActivity.kt`: Splash screen implementation
      - **data**
        - `PomodoroDatabase.kt`: Room database setup
        - `PomodoroDao.kt`: Database access objects
        - `PomodoroEntity.kt`: Data entities
      - **ui**
        - `TimerFragment.kt`: Main timer interface
        - `HistoryFragment.kt`: Session history view
        - `AboutFragment.kt`: About section
      - **utils**
        - `AccelerometerManager.kt`: Motion detection
        - `NotificationHelper.kt`: Notification handling
    - **res**
      - **layout**: UI layout files
      - **navigation**: Navigation graphs
      - **values**: Resource files

## 🔧 Usage

1. Launch the app
2. Select your desired Pomodoro duration using the circular selector
3. Press START to begin your focus session
4. Keep your phone steady - moving it will interrupt the session
5. View your progress in the History section
6. Check your statistics in the bar chart visualization

## 📱 Supported Devices

- Android 5.0 (Lollipop) and above
- Both phones and tablets
- Requires accelerometer sensor

## 🔐 Permissions

- `android.permission.VIBRATE`: For notifications
- `android.permission.SENSOR`: For accelerometer access

## 📚 Libraries Used

- AndroidX Navigation Components
- Room Database
- MPAndroidChart
- Material Design Components
- ViewModel & LiveData
- Kotlin Coroutines

## 👤 Author

t2ne

## 🙏 Acknowledgments

- Material Design for Android
- MPAndroidChart library
- Android Jetpack components
