# Declarative Plugin

## Building

To build the agp-experimental/declarative, you need to also have Android Studio codebase checked out
and pointed to by the AGP_WORKSPACE_LOCATION environment variable. It is needed to access some of the testing
infrastructure like accessing SDK, NDK and services like exploring APKs, AARs, etc...
You also need to have the CUSTOM_REPO environment variable set. 

So for example this is what I use on my mac
```
AGP_WORKSPACE_LOCATION=/Users/jedo/src/studio-main
CUSTOM_REPO=$AGP_WORKSPACE_LOCATION/out/repo:$AGP_WORKSPACE_LOCATION/prebuilts/tools/common/m2/repository
```

To build : `gw publish`

### Running tests

To run the test in Gradle : ```gw tests:test``` or click on the Play in the gutter.

### Design docs 

There is a high level design doc available [here](go/gradle-declarative), a deeper design doc 
will be written once the project is founded.

### Code organization

There are 4 modules in the current workspace 

| module | content |
|--------|---------|
| buildSrc | convention plugins and Constants used to build the project |
| api | public APIs of the plugin |
| impl | implementation of the plugins | 
| tests | integration tests |

There are 2 plugins, a `Project` one and a `Settings` one. The `Project` plugin has the core of 
the declarative functionality : 
1. Reads the build.gradle.toml
2. Applies the configured plugins 
3. Reflectively populate the extension objects
4. Configure the dependencies/

The `Settings` plugin will do a similar job on the `Settings` object : 
1. Reads the settings.gradle.toml
2. Configure the declarative plugins on each project 
3. Provide basic plugin management capability.

contact: `jedo@google.com` for questions.