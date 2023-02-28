
unset CLASSPATH
setenv JAVA_HOME /daqfs/java/jdk-11.0.2

setenv ERSAP_HOME `pwd`/myErsap
setenv ERSAP_USER_DATA `pwd`/user_data

setenv JAVA_TOOL_OPTIONS "-XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler"
