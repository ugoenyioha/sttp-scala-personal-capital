# STTP-Spark-Personal-Capital

Scala Library for querying [Personal Capital](https://www.personalcapital.com) APIs.

Inspired by [Haochi's Personal Capital Python Library](https://github.com/haochi/personalcapital)

## How to use

1. Create a file application.conf in your resources folder

```hcl
username="ENTER USERNAME"
password="ENTER PASSWORD"
```

2. Study this usage example

```scala
    for {
        config <- loadConfigF[IO, Config]
        session <- getSession(config).login()
        ostream <- outputStream(new File(this.asInstanceOf[Any].getClass.getResource("/application.conf").getPath))
        (resp, session) <- session.fetch("/newaccount/getAccounts")
        exitCode <- IO(ExitCode.Success)
        balances = _accounts.getAll(parse(resp).getOrElse(Json.Null))
        _ <- saveConfigToStreamF[IO, Config](session.config, ostream, ConfigRenderOptions.defaults().setJson(false))
    } yield exitCode
```
