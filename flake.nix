{
  description = "Burpscript";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/23.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:

    flake-utils.lib.eachDefaultSystem (system:
    let
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.default = let
        packages = with pkgs; [
          jdk17
          gradle
          kotlin

          # For tests
          python3
          nodePackages.npm
        ];
      in  pkgs.mkShell {
        inherit packages;

        shellHook = ''
        export JAVA_HOME=${pkgs.jdk17}
        export PATH=${pkgs.lib.makeBinPath packages}:$PATH
        export BURPSCRIPT_NIX=1
        '';
      };
    });
}
