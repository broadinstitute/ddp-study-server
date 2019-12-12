=pod
    This script generates SQL statements which import institution data when executed
    For now, it generates SQL that inserts records to the "city" and "institution" table
    The script should be adjusted and re-run as soon as the schema is changed
    Usage: ./import_institutions_xml.pl <xml-file-with-institution-def>
=cut

#!/usr/bin/perl

use strict;
use warnings;

use Data::Dumper;
use XML::Twig;

sub main {
    my $xml_fname = shift;
    die "XML file with institutions must be specified!" unless $xml_fname;
    die "XML file '$xml_fname' does not exist!" unless -e $xml_fname;

    # We exclude all but "real" US states (such as Guam, Puerto Rico, Virgin Islands etc.)
    my %us_states = map { $_ => 1 } qw(
        AL AK AZ AR CA CO CT DE DC FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT
        NE NV NH NJ NM NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY
    );

    my $twig = new XML::Twig(
        twig_handlers => {
            # The walker function
            row => sub {
                my ($twig, $row) = @_;
                my ($name, $city, $state_code, $phone) = (
                    $row->first_child('hospital_name')->text,
                    $row->first_child('city')->text,
                    $row->first_child('state')->text,
                    $row->first_child('phone_number')->text,
                    $row->att('_uuid'),
                    $row->first_child('hospital_type')->text,
                );
                $name = canonicalize_name($name);
                $city = canonicalize_name($city);
                return unless $us_states{$state_code};
                # Generating the UUID by taking first letters of the name and joining them with the phone number
                # For example: "Baptist Medical Center - Beaches" --> "BMCB-9042472900"
                my $uuid = join('', grep { /[A-Z]/ }  map { substr $_, 0, 1 } split / /, $name);
                $uuid .= "-$phone";
                print "    -- Data for institution $uuid\n\n";
                print create_city_sql($state_code, $city);
                print create_inst_sql($uuid, $state_code, $city, $name);
            }
        }
    );
    $twig->parsefile($xml_fname);
}

# Removes excessive spacess, decapitalizes the string and then capitalizes the first letter of each word
# Example: "BAPTIST MEDICAL CENTER - BEACHES" --> "Baptist Medical Center - Beaches"
sub canonicalize_name {
    return join ' ', map { ucfirst lc } split /\s+/, $_[0];
}

# Use INSERT IGNORE because city information is not expected to change and even if it changes,
# it has no UNIQUE key to use for updating the record
sub create_city_sql {
    my ($state_code, $city) = @_;
    return <<CITY_SQL;
    INSERT IGNORE INTO city (state_id, name) VALUES (
        (
            SELECT country_subnational_division_id FROM country_subnational_division WHERE country_address_info_id = (
                SELECT
                    country_address_info_id
                FROM
                    country_address_info
                WHERE
                    subnational_division_type_label = 'State'
                    AND code = 'US'
            ) AND code = '$state_code'
        ),
        "$city"
    );

CITY_SQL
}

# Use ON DUPLICATE KEY UPDATE because institution information is likely to be changed
# "institution_guid" column is UNIQUE and we use it for updating everything else which is a subject to change
sub create_inst_sql {
    my ($uuid, $state_code, $city, $name) = @_;
    return <<INST_SQL;
    INSERT INTO institution(
        institution_guid, city_id, name
    )
    VALUES(
        '$uuid',
        (
            SELECT city_id FROM city WHERE state_id = (
                (
                    SELECT country_subnational_division_id FROM country_subnational_division WHERE country_address_info_id = (
                        SELECT
                            country_address_info_id
                        FROM
                            country_address_info
                        WHERE
                            subnational_division_type_label = 'State'
                            AND code = 'US'
                    ) AND code = '$state_code'

                )
            ) AND name = "$city"
        ),
        "$name"
    )
    ON DUPLICATE KEY UPDATE
        city_id = (
            SELECT city_id FROM city WHERE state_id = (
                (
                    SELECT country_subnational_division_id FROM country_subnational_division WHERE country_address_info_id = (
                        SELECT
                            country_address_info_id
                        FROM
                            country_address_info
                        WHERE
                            subnational_division_type_label = 'State'
                            AND code = 'US'
                    ) AND code = '$state_code'

                )
            ) AND name = "$city"
        ),
        name = "$name"
;

INST_SQL
}

# Program flow starts here
main($ARGV[0]);
