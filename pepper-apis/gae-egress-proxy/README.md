## Setting up a new Proxy VM

```bash
$ ./init-vm.sh ...
$ ./init-firewall.sh ...
$ ./build-and-push.sh
$ ./pepper-squid.sh ... deploy
```

## References

* Squid configuration [docs][docs].
* See what version of squid is available [on ubuntu][ubuntu].

[docs]: http://www.squid-cache.org/Doc/config/
[ubuntu-search]: https://packages.ubuntu.com/search?keywords=squid

## Credits

Docker image is based on:
* [datadog/squid](https://hub.docker.com/r/datadog/squid)
* [sameersbn/docker-squid](https://github.com/sameersbn/docker-squid)
