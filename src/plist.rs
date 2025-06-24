use crate::{errors::Errors, Cli};
use colored::Colorize;
impl Cli {
    pub fn load_plist(&self) {
        println!("loading plist at {}", self.args.plist_path.cyan());
        let output = match std::process::Command::new("launchctl")
            .arg("load")
            .arg(&self.args.plist_path)
            .output()
        {
            Ok(f) => f,
            Err(e) => {
                eprintln!("{}: {}", Errors::CannotRunLaunchctl, e);
                return;
            }
        };
        if !output.stderr.is_empty() {
            println!(
                "failed to load plist, stderr: \n {}",
                String::from_utf8_lossy(&output.stderr).red()
            );
            println!("{}", "make sure that plist is unloaded first! load throws error when the plist is already loaded.".bright_red())
        }
    }
    pub fn unload_plist(&self) {
        println!("unloading plist at {}", self.args.plist_path.bright_red());
        let output = match std::process::Command::new("launchctl")
            .arg("unload")
            .arg(&self.args.plist_path)
            .output()
        {
            Ok(o) => o,
            Err(e) => {
                eprintln!("{}: {}", Errors::CannotRunLaunchctl, e);
                return;
            }
        };
        println!(
            "Stdout: \n{}\n Stderr: \n{}",
            String::from_utf8_lossy(&output.stdout).cyan(),
            String::from_utf8_lossy(&output.stderr).bright_red()
        )
    }
}
