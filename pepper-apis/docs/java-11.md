# Java 11 and OpenJDK

Here's a short guide on switching to Java 11. You should be using the OpenJDK
version instead of Oracle's JDK.

## Installing Java 11

### macOS

On macOS, the preferred way is to use [Homebrew][brew]. You may also want a "version
manager" to easily switch between Java versions, like the [jenv] tool.

To install:

```
$ brew tap homebrew/cask-versions
$ brew cask install java11
```
You can also setup jenv to switch between Java 8 and Java 11:
```
$ brew install jenv
$ echo 'export PATH="$HOME/.jenv/bin:$PATH"' >> ~/.bash_profile
$ echo 'eval "$(jenv init -)"' >> ~/.bash_profile
```
(Replace the `.bash_profile` for `.zshrc` if using zsh or look at [jenv] docs if using something else).

Configure `jenv` by registering the Java versions you will be switching to and from:

```
$ jenv add /Library/Java/JavaVirtualMachines/openjdk-11.0.2.jdk/Contents/Home
$ jenv add /Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home
```
Now, we can set Java 11 as the default, and make sure things are good:
```
$ jenv global 11.0
$ java --version
```

That last command should say something about Java 11 and OpenJDK. You should
also update maven, to version `3.6.0` or newer.

### Windows

You're on your own. This likely involves using an installer, or manually
putting files into your `C:\Program Files\Java\` directory.

### Linux

You're on your own. It's unlikely you'll need instructions here. Just use your
package manager and look for the `openjdk-11` package or similar.

### Manual

You may also manually [download][dl-11] OpenJDK 11 and setup it up yourself.

[brew]: https://brew.sh/
[jenv]: https://www.jenv.be/
[dl-11]: https://jdk.java.net/11/

## Configuring Intellij

Once you have the `pepper-apis` project open:

1. Navigate to `File > Project Structure`.
2. Under `Project Settings > Project`, make sure `Project SDK` is `11`.
3. If it's not available, add a new one by pointing to the `Contents/Home` directory of your openjdk11 installation.
4. Under `Project language level`, make sure `11` is selected.
5. Save.

If you now run `Build > Rebuild Project`, you should see `javac 11.0.2` mentioned in the messages.

## Troubleshooting

1. If you see errors related to "class loader", "failed to instantiate type"
"unable to make private ... accessible", or similar, this likely means some
code in the project or dependencies is using reflection and trying to access
classes the "old way". The default in Java11 is `--illegal-access=permit`
which should allow reflective access, so this error shouldn't occur.

2. If you see any odd issues with a dependency and can't figure it out from a
quick debugging session, try updating the dependency version.
