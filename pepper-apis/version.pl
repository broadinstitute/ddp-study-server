#!/usr/bin/perl

# Bumps up the major or/and minor app versions in the POM and the version file
# Params: flags indicating what version (major, minor, or both) should be bumped up
# Example: $ perl version.pl --major --minor  # Updates both major and minor version

use strict;
use warnings;
use Getopt::Long;

my $VERSION_FILE = '../pepper-angular/version.txt';

sub get_current_backend_version {
    my $maven_log_regex = '^\\[';
    my $download_regex = '^Download';
    my $cmdline = "mvn help:evaluate -Dexpression=project.version | grep -v '$maven_log_regex' | grep -v '$download_regex'";
    my $current_version = qx/$cmdline/;
    chomp $current_version;
    return $current_version;
}

sub calculate_next_app_version {
    my ($current_version, $bump_major, $bump_minor) = @_;
    my $next_version = $current_version;
    $next_version += 1 if $bump_major;
    $next_version += 0.1 if $bump_minor;
    return sprintf("%.1f", $next_version);
}

sub update_backend_version {
    my $next_version = shift;
    print "Updating the back-end app version in the POM file...\n";
    my $mvn_output = qx/mvn --batch-mode versions:set -DnewVersion=$next_version/;
    if ($?) {
        die "Failed to bump up the app version to $next_version. Maven said:\n$mvn_output";
    } else {
        print "Successfully bumped up the app version to $next_version in the POM file\n";
    }
}

sub update_frontend_version {
    my $next_version = shift;
    open my $fh, '>', $VERSION_FILE or die "Failed to open the front-end version file";
    print "Updating the front-end version in version.txt...\n";
    print $fh $next_version;
    print "Successfully bumped up the front-end version to $next_version in the version file\n";
}

sub validate_version {
    my $version = shift;
    return $version =~ /^\d+\.\d{1}$/;
}

sub slurp_file {
    my $file_name = shift;
    local $/ = undef;
    open my $fh, '<', $file_name or die "Failed to open the file $file_name";
    return <$fh>;
}

sub main {
    my ($bump_major, $bump_minor);
    my $version_pattern = '^[0-9]+\\.[0-9]{1}$';

    # Reading command-line params
    GetOptions("major" => \$bump_major, "minor" => \$bump_minor);
    my $usage_example = '$ ./version.pl <bump_major> <bump_minor>. At least 1 param is required. E.g. ./version.pl --major';
    print "Usage: $usage_example\n" and exit if !$bump_major && !$bump_minor;

    # Updating the back-end version
    print "Reading the current app version...\n";
    my $current_backend_version = get_current_backend_version();
    die "Version '$current_backend_version' looks incorrect. Must match $version_pattern (e.g. 1.5)" unless validate_version($current_backend_version);
    print "Current version is $current_backend_version\n";
    my $next_backend_version = calculate_next_app_version($current_backend_version, $bump_major, $bump_minor);
    print "Next backend version will be $next_backend_version...\n";
    update_backend_version($next_backend_version);

    # Updating the front-end version
    my $current_frontend_version = slurp_file($VERSION_FILE);
    chomp $current_frontend_version;
    die "Version '$current_frontend_version' looks incorrect. Must match $version_pattern (e.g. 1.5)" unless validate_version($current_frontend_version);
    my $next_frontend_version = calculate_next_app_version($current_frontend_version, $bump_major, $bump_minor);
    update_frontend_version($next_frontend_version);
}

main();
