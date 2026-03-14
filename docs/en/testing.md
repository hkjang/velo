# Velo WAS Testing Guide

This guide explains how to run the `LiveWarDeploymentTest` in the `velo-was` project using Git Bash. 
This test is intended to be executed in a Bash environment (`git bash` recommended on Windows) and validates the WAR deployment functionality within the Velo WAS environment.

## Execution Command

Open your terminal (ensure it is Git Bash, WSL, or a similar Bash-compatible shell) and execute the following command from the root of the project:

```bash
JAVA_HOME="D:\\project\\velo\\.tools\\jdk\\jdk-21.0.10+7" \
"D:/project/velo/.tools/maven/apache-maven-3.9.13/bin/mvn" test \
  -pl was-bootstrap \
  -am \
  -Dtest=LiveWarDeploymentTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -f d:/project/velo/pom.xml 2>&1 | tail -80
```

### Breakdown of the command:
1. `JAVA_HOME="..."`: Assigns the specific JDK path required to build and test the project. Here we use JDK 21.
2. `".../bin/mvn" test`: Invokes Maven to run tests using the local Maven installation.
3. `-pl was-bootstrap -am`: Test the `was-bootstrap` module and also make its dependencies (`-am`).
4. `-Dtest=LiveWarDeploymentTest`: Restricts the test execution to only `LiveWarDeploymentTest.java`.
5. `-Dsurefire.failIfNoSpecifiedTests=false`: Ensures Maven doesn't fail if the specified test doesn't exist in other modules.
6. `-f d:/project/velo/pom.xml`: Points to the specific root `pom.xml`.
7. `2>&1 | tail -80`: Redirects standard error to standard output and only displays the last 80 lines for conciseness.

## Success Criteria

If the test is successful, you will see output indicating that `LiveWarDeploymentTest` has passed, such as:
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

If it fails, review the logs in `target/surefire-reports` under the `was-bootstrap` module or remove the `| tail -80` part to inspect the full trace.
