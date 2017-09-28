# Auto Persisted JSON
* Add auto persisting functionality to Alibaba Fastjson
* Useful when application using a json as config file
### Usage
#### Setup
1. Add the following to your `pom.xml` as dependency
    ```xml
    <dependencies>
        <groupId>pw.highprophet</groupId>
        <artifactId>autopersisted-json</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependencies>
    ```
1. You can resolve the dependency by adding the following repository to your `pom.xml` or `settings.xml`
    ```xml
    <repository>
        <id>Flora</id>
        <name>Flora Repository</name>
        <url>https://raw.github.com/HighProphet/mvn-repo/master/</url>
    </repository>
    ```
#### Use
1. create json file and a `JSON` Object related to it:
    ```
    JSONObject json = AutoPersistedJSONFactory.getPersistedJSON(targetFile,JSONObject.class);
    ```
    this will create a `JSONObject json` proxied by `JSONPersistHandler`
    which runs a Thread that writes the content of the `json` to the `targetFile` after modification
1. You can also create persistedJSON Object based on existed `JSON`:
    ```
    JSONObject json = AutoPersistedJSONFactory.getPersistedJSON(unpersistedJson,targetFile);
    ```
    **note:** This method is a generic method. If `upersistedJson` is a `JSONArray` the return type will be `JSONArray`. 