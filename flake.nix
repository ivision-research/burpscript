{
  description = "Burpscript";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/25.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:

    flake-utils.lib.eachDefaultSystem (system:
    let
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.default = let
        
        jdk = pkgs.jdk24;
        gradle = pkgs.gradle-unwrapped.override {
          java = jdk;
        };

        packages = with pkgs; [
          kotlin

          # For tests
          python3
          nodePackages.npm
        ] ++ [
          jdk
          gradle
        ];
      in  pkgs.mkShell {
        inherit packages;

        shellHook = ''
        export JAVA_HOME=${pkgs.jdk24}
        export PATH=${pkgs.lib.makeBinPath packages}:$PATH
        export BURPSCRIPT_NIX=1
        '';
      };
    });
}
