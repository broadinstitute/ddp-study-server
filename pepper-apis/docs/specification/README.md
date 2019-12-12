# Pepper OpenAPI
This is a (very) rough OpenAPI specification for the Pepper platform.

## Browsing the documentation
[ReDoc](https://github.com/Rebilly/ReDoc) is currently used to provide a UI for browsing the documentation. To view the documentation, you must have [Node.js](https://nodejs.org/en/) installed. The easiest way to do this is via [Homebrew](https://brew.sh/):
```shell
brew install node
```

Once you have Node.js installed, start a local ReDoc server daemon with:
```shell
cd /path/to/ddp/pepper-apis/docs/specification
npm install && npm start
```

Once the server is running,  you can navigate to `http://127.0.0.1:8080` to browse the docs! The server will monitor the directory for any changes, by default, and make them available on a browser refresh.