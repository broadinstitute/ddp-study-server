## Setting up a new Proxy VM

```bash
$ ./init-vm.sh ...
$ ./init-firewall.sh ...
$ ./build-and-push.sh
$ ./pepper-squid.sh ... deploy
```

## Connecting to Proxy VM

Once setup, the GAE services will need to be configured to use the appropriate
VPC connector. These services may reference the internal DNS for the proxy VM,
like so:

```
http://[nstance_name].c.[project_id].internal:3128
```

## References

* Squid configuration [docs][docs].
* See what version of squid is available [on ubuntu][ubuntu-search].

[docs]: http://www.squid-cache.org/Doc/config/
[ubuntu-search]: https://packages.ubuntu.com/search?keywords=squid

## Credits

Docker image is based on:
* [datadog/squid](https://hub.docker.com/r/datadog/squid)
* [sameersbn/docker-squid](https://github.com/sameersbn/docker-squid)
