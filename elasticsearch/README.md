# pepper-elasticsearch

## Security

The security model in Elasticsearch is mainly composed of user accounts and
roles. Accounts authenticate the user and proves identity. Each account will
have roles that authorize the account to certain access privileges. The scope
of each privilege is explained [here][es-role-privs].

[es-role-privs]: https://www.elastic.co/guide/en/elastic-stack-overview/6.6/security-privileges.html

### Note about Roles

Each role allows us to tweak privileges to the cluster, to indices, and to
Kibana spaces. We have a distinction between PHI and non-PHI roles, where PHI
roles have privileges to sensitive data fields. We should have roles that are
by-default non-PHI, and any PHI roles should be named with a `_phi` suffix.

### Note about Roles Names

The role names should be self-descriptive to help us understand what privileges
the role allows. Typically, we split roles into different categories like
`cluster`, `kibana`, and `index`, since those are the kinds of privileges we
can customize. We should also indicate whether it's `read` or `write`, and if
it has full privileges we use `admin`. And as mentioned above, if the role
allows reading sensitive data, we should include the `_phi` suffix.

### Note about Kibana Spaces

A "space" in Kibana is simply a way to organize dashboards and visualizations
(aka mostly UI-related things). We might not necessarily use these features,
but since there are going to be users logging into Kibana, we should control
the security around this. Particularly, we should have a separate Kibana space
per umbrella.

### Pepper Roles

There are a few roles for Pepper's own use, with role names starting with
`pepper_`. Pepper admin accounts should be assigned the appropriate roles. For
API and machine-to-machine usage, we should only need the
`pepper_index_admin_phi` role.

### Umbrella Specific Roles

We should have a set of roles for each umbrella. Currently, there is no need
for roles that allow write privileges, since any writes to the index data will
be overwritten by Pepper's data upload process. Most of the time, we just need
to grant read privileges to index data. For API and machine-to-machine usage,
we should just need the index read roles.

For non-PHI roles with read privileges, the read access is granted based on a
whitelist. We spell out explicitly the exact indices and the exact fields that
are granted for reading.

Lastly, each umbrella should also have its own Kibana space, so we should have
a role for that as well.

### Create/Update Indices/Roles/Users

Use the `elastic.sh` script to create or update indices, roles, and users.
Generally, the script commands take a json filename that will serve as the
request payload when making the appropriate API calls to Elasticsearch. The
commands will derive the index/role name from the filename itself.

For users, the credentials are stored in `vault`. Use the `render.sh` script to
render the user secrets and the resulting file in `output-config` for
uploading. You'll need the `jq` command installed locally.

