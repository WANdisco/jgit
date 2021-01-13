workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "bazel_skylib",
    sha256 = "2ea8a5ed2b448baf4a6855d3ce049c4c452a6470b1efd1504fdb7c1c134d220a",
    strip_prefix = "bazel-skylib-0.8.0",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/0.8.0.tar.gz"],
)

load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "0.29.0")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "09a035e98077dce549d5f6a7472d06c4b8f792d2")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "MAVEN_LOCAL",
    "maven_jar",
)
load(
    "//tools:maven_custom.bzl",
    "WANDISCO_ASSETS",
)

JMH_VERS = "1.21"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "442447101f63074c61063858033fbfde8a076873",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "7aac374614a8a76cad16b91f1a4419d31a7dcda3",
)

maven_jar(
    name = "jopt",
    artifact = "net.sf.jopt-simple:jopt-simple:5.0.4",
    attach_source = False,
    sha1 = "4fdac2fbe92dfad86aa6e9301736f6b4342a3f5c",
)

maven_jar(
    name = "math3",
    artifact = "org.apache.commons:commons-math3:3.6.1",
    attach_source = False,
    sha1 = "e4ba98f1d4b3c80ec46392f25e094a6a2e58fcbf",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.55",
    sha1 = "bbd40e5aa7aa3cfad5db34965456cee738a42a50",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.1",
    sha1 = "a1551373315ffc2f96130a0e5704f74e151777ba",
)

# WANdisco maven assets
# TODO: check how to make this provided scope in LFS server same as we do in POM.xml
_GERRIT_GITMS_VERSION = "1.1.0.1-TC18-SNAPSHOT"

maven_jar(
    name = "gerrit-gitms-interface",
    artifact = "com.wandisco:gerrit-gitms-interface:" + _GERRIT_GITMS_VERSION,
    repository = WANDISCO_ASSETS,
    #    repository = MAVEN_LOCAL,
    #    sha1 = 213e4234
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.6",
    sha1 = "1afe5621985efe90a92d0fbc9be86271efbe796f",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.10",
    sha1 = "acc54d9b28bdffe4bbde89ed2e4a1e86b5285e2b",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
)

maven_jar(
    name = "jcl-over-slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:1.7.5",
    sha1 = "0cd5970bd13fa85f7bed41ca606d6daf7cbf1365",
)

maven_jar(
    name = "log4j-core",
    artifact = "org.apache.logging.log4j:log4j-core:2.14.0",
    sha1 = "e257b0562453f73eabac1bc3181ba33e79d193ed",
)

maven_jar(
    name = "log4j-api",
    artifact = "org.apache.logging.log4j:log4j-api:2.14.0",
    sha1 = "23cdb2c6babad9b2b0dcf47c6a2c29d504e4c7a8",
)

maven_jar(
    name = "slf4j-log4j12",
    artifact = "org.slf4j:slf4j-log4j12:1.7.5",
    sha1 = "6edffc576ce104ec769d954618764f39f0f0f10d",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.5",
    sha1 = "6b262da268f8ad9eff941b25503a9198f0a0ac93",
)

maven_jar(
    name = "servlet-api-3_1",
    artifact = "javax.servlet:javax.servlet-api:3.1.0",
    sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.15",
    sha1 = "b686cd04abaef1ea7bc5e143c080563668eec17e",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.6",
    sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "hamcrest-library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
)

maven_jar(
    name = "hamcrest-core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "mockito-core",
    artifact = "org.mockito:mockito-core:2.23.0",
    sha1 = "497ddb32fd5d01f9dbe99a2ec790aeb931dff1b1",
)

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:1.9.0",
    sha1 = "8cb0d5baae526c9df46ae17693bbba302640538b",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:1.9.0",
    sha1 = "37b5703b4a6290be3fffc63ae9c6bcaaee0ff856",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.2",
    sha1 = "3edcfe49d2c6053a70a2a47e4e1c2f94998a49cf",
)

JETTY_VER = "9.4.11.v20180605"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "66d31900fcfc70e3666f0b3335b6660635154f98",
    src_sha1 = "930c50de49b9c258d5f0329426cbcac4d3143497",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "926def86d31ee07ca4b4658833dc6ee6918b8e86",
    src_sha1 = "019bc7c2a366cbb201950f24dd64d9d9a49b6840",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "58353c2f27515b007fc83ae22002feb34fc24714",
    src_sha1 = "e7d832d74df616137755996b41bc28bb82b3bc42",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "20c35f5336befe35b0bd5c4a63e07170fe7872d7",
    src_sha1 = "5bc30d1f7e8c4456c22cc85999b8cafd3741bdff",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "d164de1dac18c4ca80a1b783d879c97449909c3b",
    src_sha1 = "02c0caba292b1cb74cec1d36c6f91dc863c89b5a",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "f0f25aa2f27d618a04bc7356fa247ae4a05245b3",
    src_sha1 = "4e5c4c483cfd9804c2fc5d5751866243bbb9d740",
)
