1. **Folder generation**
```bash
mvn archetype:generate -DgroupId=com.giga.spring \
    -DartifactId=gigaspring \
    -Dversion=1.0-SNAPSHOT \
    -Dpackage=com.giga.spring \
    -DarchetypeArtifactId=maven-archetype-quickstart \
    -DinteractiveMode=false
```

2. **Install plugins / JDK**
Inside the `<build>` in `pom.xml` section: the project is now configured to use JDK 21.
Ensure you have JDK 21 installed and that your `JAVA_HOME` and `PATH` are set appropriately for PowerShell. Example (PowerShell):

```powershell
# Set JAVA_HOME to your JDK 21 installation (example path)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
```

The `maven-compiler-plugin` uses `<release>21` to compile for Java 21.
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
            </configuration>
        </plugin>

        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>3.1.0</version>
            <configuration>
                <mainClass>com.giga.spring.Main</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

3. Build and run
Build without tests (useful for quick verification):

```powershell
mvn -DskipTests package
```

Run tests:

```powershell
mvn test
```

If you need to install a local package into the local Maven repo, run:

```powershell
./package.sh
```