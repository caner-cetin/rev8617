use std::net::TcpStream;

use crate::{errors::Errors, Cli};
use anyhow::{anyhow, Result};
use tungstenite::{connect, http::StatusCode, stream::MaybeTlsStream, WebSocket};

impl Cli {
    pub fn connect_to_dsp(&self) -> Result<WebSocket<MaybeTlsStream<TcpStream>>> {
        let (sock, response) = match connect(self.dsp_url.as_str()) {
            Ok(w) => w,
            Err(err) => {
                return Err(anyhow!(Errors::CannotConnectToDSP {
                    uri: self.dsp_url.clone(),
                    error: err
                }));
            }
        };
        if response.status() != StatusCode::OK {
            return Err(anyhow!(
                "dsp connection at {} returned non 200 error code ({})",
                self.dsp_url.as_str(),
                response.status()
            ));
        }
        return Ok(sock);
    }
}
