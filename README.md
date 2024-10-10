<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-consistent-versions-idea-plugin"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# Gradle Consistent Versions Idea Plugin

An Idea plugin for use with [gradle consistent versions](https://github.com/palantir/gradle-consistent-versions)

## Features:
### Automatic gradle refresh when changes are detected in `versions.props`
No more worrying about running `writeVersionsLock` simply save your `versions.props` file and the plugin will automatically trigger write locks and refresh the project.

![Write Versions Lock](media/write-versions-lock.gif)

### Auto-complete for packages and versions in `versions.props`
First ensure version `2.26.0` or higher of the `gradle-consistent-versions` plugin is applied in your project, and you will get auto-complete suggestions directly in `versions.props`.

![Remote Suggestions](media/suggestions.gif)

Skip the guessing game get the latest version by typing `latest`.

![Latest Suggestion](media/latest.gif)

Superfast auto-complete directly from your gradle cache allows for full package auto complete.

![Cache Suggestions](media/cache.gif)

### Project Level Disable For On Save Lock Writing
Either disable via the settings page or add the following to a projects `.idea/gcv-plugin-settings.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectSettings">
    <option name="enabled" value="false" />
  </component>
</project>
```

![Settings Page](media/settings.gif)

### Syntax highlighting
`versions.props` files now have syntax highlighting. With highlighting rules to clearly mark `dependency-upgrader` controls.

![Highlighter](media/highlighting.png)

### Commenter support
Create comments automatically using the your default intellij shortcuts.

![Commenter](media/commenter.gif)
