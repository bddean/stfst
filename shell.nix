# The Android build needs a cross std and uses the flake instead; see
# android/build-native.sh.
{ pkgs ? import <nixpkgs> { } }:
pkgs.mkShellNoCC {
  packages = [ pkgs.cargo pkgs.python314 pkgs.jq pkgs.hfst pkgs.foma ];
}
