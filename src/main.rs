use anyhow::{anyhow, Result};
use clap::{Parser, Subcommand};
use errors::Errors;
use serde_json::Value;
use tungstenite::Message;
use url::Url;
mod errors;
mod plist;
mod ws;

#[derive(Parser)]
struct Args {
    #[command(subcommand)]
    command: Commands,

    #[arg(
        help = "Background process of CamillaDSP, must be absolute path.",
        global = true,
        default_value = "/Users/canercetin/Git/rev8617/config/com.user.camilladsp.plist"
    )]
    plist_path: String,
    #[arg(
        help = "CamillaDSP websocket port",
        global = true,
        default_value = "9090"
    )]
    dsp_port: u16,
    #[arg(
        help = "CamillaDSP websocket addr",
        global = true,
        default_value = "127.0.0.1"
    )]
    dsp_address: String,
}

struct Cli {
    args: Args,
    dsp_url: Url,
}

impl Cli {
    fn new() -> Result<Cli> {
        let args = Args::parse();
        if !(std::fs::exists(&args.plist_path)?) {
            return Err(anyhow!(Errors::PlistDoesNotExist(args.plist_path)));
        }
        let dsp_url =
            match Url::parse(format!("ws://{}:{}", args.dsp_address, args.dsp_port).as_str()) {
                Ok(uri) => uri,
                Err(err) => {
                    return Err(anyhow!(
                        "invalid DSP address ({}) or port ({}): {}",
                        args.dsp_address,
                        args.dsp_port,
                        err
                    ))
                }
            };
        Ok(Cli { args, dsp_url })
    }
}

#[derive(Subcommand)]
enum Commands {
    #[clap(
        about = "Loads background plist for running CamillaDSP now and at startup. See config folder."
    )]
    Load {},

    #[clap(about = "Unloads CamillaDSP background process.")]
    Unload {},
    #[clap(about = "CamillaDSP and REV version.")]
    Version {},
}

fn main() {
    let cli = match Cli::new() {
        Ok(f) => f,
        Err(e) => {
            eprintln!("failed to start CLI: {}", e);
            return;
        }
    };
    match &cli.args.command {
        Commands::Load {} => cli.load_plist(),
        Commands::Unload {} => cli.unload_plist(),
        Commands::Version {} => {
            let mut sock = match cli.connect_to_dsp() {
                Ok(sock) => sock,
                Err(err) => {
                    eprintln!("failed to connect to dsp: {}", err);
                    return;
                }
            };
            sock.send(Message::Text("\"GetVersion\"".into())).unwrap();
            let msg = sock.read().expect("failed to get version of camilladsp");
            let resp: Value =
                serde_json::from_str(msg.to_text().expect("cannot read camilladsp msg"))
                    .expect("cannot parse camilladsp response");
            println!(
                "CamillaDSP: {}, REV8617: ???",
                resp["GetVersion"]["value"].as_str().unwrap()
            );
        }
    }
}
