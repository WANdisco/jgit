# Allow definitions for where we should pick up our assets from.
# By default if no repository is specified in say WORKSPACE then it defaults to maven_central definition.
# This behaviour is found / specified and loaded from
# "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl", "maven_jar",

# To prevent us having to create our own version of this asset, we are just adding custom maven repository definitions to a central location.
WANDISCO_ASSETS = "WANDISCO:"
