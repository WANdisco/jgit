workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f30a992da9fc855dce819875afb59f9dd6f860cd")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "MAVEN_LOCAL",
    "maven_jar",
)
load(
    "//tools:maven_custom.bzl",
    "WANDISCO_ASSETS",
)

http_archive(
    name = "rbe_jdk11",
    sha256 = "dbcfd6f26589ef506b91fe03a12dc559ca9c84699e4cf6381150522287f0e6f6",
    strip_prefix = "rbe_autoconfig-3.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v3.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/archive/v3.1.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

JMH_VERS = "1.35"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "c14d712be8e423969fcd344bc801cf5d3ea3b62a",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "50fba446d32d22f95f51a391f3450e03af006754",
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
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.3.0",
    sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.55",
    sha1 = "bbd40e5aa7aa3cfad5db34965456cee738a42a50",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.3",
    sha1 = "c01428efa717624f7aabf4df319939dda9646b2d",
)

# WANdisco maven assets
# TODO: check how to make this provided scope in LFS server same as we do in POM.xml
_GERRIT_GITMS_VERSION = "3.0.0.1"

maven_jar(
    name = "gerrit-gitms-shared",
    artifact = "com.wandisco:gerrit-gitms-shared:" + _GERRIT_GITMS_VERSION,
    repository = WANDISCO_ASSETS,
    sha1 = 632c5626df837567c1dd17bd9ebf0a652363b397
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.2.3",
    sha1 = "13a27c856e0c8808cee9a64032c58eee11c3adc9",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.14",
    sha1 = "1194890e6f56ec29177673f2f12d0b8e627dec98",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.16",
    sha1 = "51cf043c87253c9f58b539c9f7e44c8894223850",
)

SSHD_VERS = "2.10.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "a93e59e8786cb72ecd6300c7a54a038eb930ba20",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "a1bb22888d9a90ed68ce3231946b1904cb3187f5",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.13.0",
    sha1 = "7b9e9f231f04897eff97a1a4a89b39b3834e79e7",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:5.13.0",
    sha1 = "88e9a306715e9379f3122415ef4ae759a352640d",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.15",
    sha1 = "49d94806b6e3dc933dacbd8acb0fdbab8ebd1e5d",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "commons-lang",
    artifact = "commons-lang:commons-lang:2.6",
    sha1 = "0ce1edb914c94ebc388f086c6827e8bdeec71ac2",
)

maven_jar(
    name = "jcl-over-slf4j",
    artifact = "org.slf4j:jcl-over-slf4j:1.7.5",
    sha1 = "0cd5970bd13fa85f7bed41ca606d6daf7cbf1365",
)

LOG4J_VERSION = "2.17.1"

maven_jar(
    name = "log4j-core",
    artifact = "org.apache.logging.log4j:log4j-core:" + LOG4J_VERSION,
    sha1 = "e257b0562453f73eabac1bc3181ba33e79d193ed",
)

maven_jar(
    name = "log4j-api",
    artifact = "org.apache.logging.log4j:log4j-api:" + LOG4J_VERSION,
    sha1 = "23cdb2c6babad9b2b0dcf47c6a2c29d504e4c7a8",
)

maven_jar(
    name = "slf4j-log4j12",
    artifact = "org.slf4j:slf4j-log4j12:1.7.30",
    sha1 = "6edffc576ce104ec769d954618764f39f0f0f10d",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.36",
    sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.36",
    sha1 = "a41f9cfe6faafb2eb83a1c7dd2d0dfd844e2a936",
)

maven_jar(
    name = "servlet-api",
    artifact = "jakarta.servlet:jakarta.servlet-api:4.0.4",
    sha1 = "b8a1142e04838fe54194049c6e7a18dae8f9b960",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.23.0",
    sha1 = "4af2060ea9b0c8b74f1854c6cafe4d43cfc161fc",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.9",
    sha1 = "1ea4bec1a921180164852c65006d928617bd2caf",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.13.2",
    sha1 = "8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12",
)

maven_jar(
    name = "hamcrest",
    artifact = "org.hamcrest:hamcrest:2.2",
    sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:4.8.1",
    sha1 = "d8eb9dec8747d08645347bb8c69088ac83197975",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.24.2",
    sha1 = "ebbf338e33f893139459ce5df023115971c2786f",
)

BYTE_BUDDY_VERSION = "1.12.18"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "875a9c3f29d2f6f499dfd60d76e97a343f9b1233",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "417a7310a7bf1c1aae5ca502d26515f9c2f94396",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.3",
    sha1 = "1049c09f1de4331e8193e579448d0916d75b7631",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.10.1",
    sha1 = "b3add478d4382b78ea20b1671390a858002feb6c",
)

JETTY_VER = "10.0.15"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "17e21100d9eabae2c0f560ab2c1d5f0edfc4a57b",
    src_sha1 = "989ecc16914e7c8f9f78715dd97d0c511d77a99f",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "ae9c2fd327090fc749a6656109adf88f84f05854",
    src_sha1 = "1cae575fc9f3d9271507642606603cca7dc753e8",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "d1e941f30300d64b122d5346f1599ecaa8e270ba",
    src_sha1 = "7b04c7d3dc702608306935607bf73ac871816010",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "53c4702201c33501bc37a982e5325b5f11084a4e",
    src_sha1 = "2cf03c695ea19c1af5668f5c97dac59e3027eb55",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "4481d9593bb89c4da016e49463b0d477faca06dc",
    src_sha1 = "c9f241cce63ac929d4b8bd859c761ba83f4a3124",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "eb8901d419e83f2f06809e0cdceaf38b06426f01",
    src_sha1 = "757ee2bd100c5bd20aebc7e2fdc4ceb99f23b451",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "0cde62dd87845dd6c0c7f07db6c901e7d020653b",
    src_sha1 = "135448f8b3b3b06f7f3312d222992525ae4bdd25",
)

BOUNCYCASTLE_VER = "1.73"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "2838f8c35e6e716349ce780c9c88271cab32065d",
    src_sha1 = "3ea8d8e88569024cb37c303384d33f12e8be1ca3",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "4bd3de48e5153059fe3f80cbcf86ea221795ee55",
    src_sha1 = "665f03dc0b10ef2fc90a11c28e48c84a3a9a7323",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "073a680acd04b249a6773f49200092cadb670bf0",
    src_sha1 = "573ebc8e83bc846e815e68e4c624f2d0aef941b9",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "fd41dae0f564a93888ed5ade426281de94824717",
    src_sha1 = "e11d418a87536d6f5a9537f7cb1f15a3e5c505e9",
)
