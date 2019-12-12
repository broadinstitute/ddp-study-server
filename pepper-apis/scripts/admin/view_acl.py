import subprocess as s
import argparse
import time

# see installation instructions for dependencies in README.md
from mysql.connector import (connection)
import sys

class User:
    def __init__(self, account, address):
        self.account = account.strip()
        self.address = address.replace('/32','').strip()

parser = argparse.ArgumentParser(description='Reports various access control related information about pepper resources.  Requires gcloud and jq command line tools.  You must do a gcloud auth login before running.')
parser.add_argument("env", help="One of dev, test, staging, or prod environment")
parser.add_argument("sqlport", help="Port on which to run sql proxy")
args = parser.parse_args()

env = args.env

# this is the list of "role" tags that should be applied to each cloudsql database
# that we want to check permissions on
dbRoles = ['pepper_apis']

# todo arz add dsm vault endpoint here as well so we don't have to move too much vault stuff around
gcpProject = "broad-ddp-" + env
vaultPath = "/secret/pepper/" + env + "/v1"
readVaultPrefix = "vault read --format=json " + vaultPath

# todo arz figure out cleaner shutdown of proxy, maybe prompt for shutdown instead of blindly shutting stuff down
sqlProxyPort = args.sqlport
print ("Shutting down any existing cloudsql proxy for port " + sqlProxyPort)
try:
    s.check_output("kill $(lsof -t -i:" + sqlProxyPort + ")", shell=True)
except:
    print ("Error shutting down process on port " + sqlProxyPort + "; continuing...")

print ("Checking ACL information for " + gcpProject)

gcloudPrefix = "gcloud --project=" + gcpProject + " "
gcloudSqlPrefix = gcloudPrefix + "sql instances describe "

databaseNames = s.check_output(gcloudPrefix + "sql instances list --format=\"json\" | jq '.[].name' -r", shell=True).decode('UTF-8').splitlines()

for dbRole in dbRoles:
    # todo will require adding role label to all databases so we can pick them up consistently across environments,
    # then add the role mapping here so we iterate over apis, housekeeping, and dsm

    dbName = s.check_output("gcloud sql instances list --format=\"json\" --filter=\"settings.userLabels.role:" + dbRole + "\" | jq -r '.[].name'", shell=True).decode('UTF-8').strip()
    databaseNames.remove(dbName)
    if dbName:
        print ("Found database " + dbName + " for role " + dbRole + " in " + env)
        allowedIPs = s.check_output(
            gcloudSqlPrefix + dbName + " --format=\"json\" | jq '.settings.ipConfiguration.authorizedNetworks[]? | select(.kind == \"sql#aclEntry\") | .name + \": \" + .value '",
            shell=True).decode('UTF-8')
        print ("Allowed IPs")
        for ip in allowedIPs.splitlines():
            print ("\t" + ip)

        print ("Opening cloudsqlproxy for " + dbName + " on port " + sqlProxyPort)
        sqlProxyProcess = s.Popen(
            "cloud_sql_proxy -instances=" + gcpProject + ":us-central1:" + dbName + "=tcp:0.0.0.0:" + sqlProxyPort,
            shell=True)
        # wait a moment for sqlproxy to startup
        time.sleep(2)


        rootPassword = s.check_output(readVaultPrefix + "/conf | jq -r .data.rootDbCredentials." + dbRole, shell=True).decode('UTF-8').strip()

        # todo will need to put the role-based root passwords into a different structure in vault
        # try vault read --format=json /secret/pepper/dev/v1/conf | jq -r .data.rootDbCredentials for example
        conn = connection.MySQLConnection(user='root', password=rootPassword,
                                          host='0.0.0.0',
                                          port=sqlProxyPort)

        cursor = conn.cursor()
        cursor.execute("select user, host from mysql.user")
        users = []
        for row in cursor.fetchall():
            user = User(row[0],row[1])
            users.append(user)
            cursor.close()
        for user in users:
            username = '\'' + user.account + '\'@' + '\'' + user.address + '\''
            if user.address != '%':
                print (user.address)
                vmName = s.check_output("gcloud compute instances list --format=\"json\" --filter=\"networkInterfaces.accessConfigs.natIP=" + user.address + "\" | jq -r '.[].name'",shell=True).decode('UTF-8').strip()
                print ("Permissions for " + username + " (" + vmName + ")")
            else:
                print ("Permissions for " + username)
            cursor = conn.cursor()
            cursor.execute("""
                select concat(privilege_type, ' on ', table_schema, '.', table_name)  from information_schema.TABLE_PRIVILEGES where grantee = %s
                union
                select concat(privilege_type, ' on ', table_schema, '.*') from information_schema.SCHEMA_PRIVILEGES where grantee =  %s
                """, (username, username))

            for permission in cursor.fetchall():
                print ("\t" + permission[0])
            cursor.close()

        time.sleep(1)
        print ("Closing sqlproxy for " + dbName)
        sqlProxyProcess.terminate()
        time.sleep(1)
    else:
        print ("No database for role " + dbRole + " in " + env)

numUnknownDatabases = len(databaseNames)
if numUnknownDatabases != 0:
    print ("There are {} databases that we don't understand:".format(numUnknownDatabases))
    print (databaseNames)