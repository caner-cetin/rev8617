use colored::Colorize;

#[derive(Debug)]
pub enum Errors {
    PlistDoesNotExist(String),
    CannotRunLaunchctl,
    CannotConnectToDSP {
        uri: url::Url,
        error: tungstenite::Error,
    },
}

impl std::fmt::Display for Errors {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::PlistDoesNotExist(path) => write!(
                f,
                "{}:{}",
                "plist path does not exist".bright_red(),
                path.cyan()
            ),
            Self::CannotRunLaunchctl => write!(f, "{}", "cannot run launchctl".bright_red()),
            Self::CannotConnectToDSP { uri, error } => {
                write!(
                    f,
                    "cannot connect to dsp at address {}: {}",
                    uri.as_str(),
                    error
                )
            }
        }
    }
}
