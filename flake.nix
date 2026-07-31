{
  description = "stfst: steno dictionaries as finite-state transducers";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, rust-overlay }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ (import rust-overlay) ];
        };

        rust = pkgs.rust-bin.stable.latest.default.override {
          extensions = [ "rust-src" "rust-analyzer" "clippy" "rustfmt" ];
        };

        # The HFST pipeline is a build-machine dependency only. It is GPLv3, and
        # nothing in the runtime crates links it: the compiler drives the
        # binaries as subprocesses and ships an hfstol artifact.
        buildTools = [ pkgs.hfst pkgs.foma ];
      in {
        devShells.default = pkgs.mkShell {
          packages = [ rust pkgs.python3 pkgs.jq ] ++ buildTools;
          shellHook = ''
            echo "stfst dev shell: $(hfst-lexc --version 2>&1 | head -1)"
          '';
        };

        # `nix develop .#minimal` for machines where HFST will not build; the
        # runtime crates and their tests need no FST toolchain.
        devShells.minimal = pkgs.mkShell {
          packages = [ rust pkgs.python3 ];
        };

        packages.default = pkgs.rustPlatform.buildRustPackage {
          pname = "stfst";
          version = "0.1.0";
          src = ./.;
          cargoLock.lockFile = ./Cargo.lock;
        };

        formatter = pkgs.nixpkgs-fmt;
      });
}
