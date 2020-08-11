FROM adoptopenjdk/openjdk10:x86_64-ubuntu-jdk-10.0.2.13-slim

USER root

RUN apt-get update \
 && apt-get install -yq --no-install-recommends \
    wget \
    curl \
    bzip2 \
    python2.7 \
    python-pip \
    python-setuptools \
    python-dev \
    python3 \
    python3-pip \
    python3-setuptools \
    python3-dev \
    build-essential \
    gfortran \
    ca-certificates \
    sudo \
    libpng-dev \
    locales \
    run-one \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
RUN apt install -yq --no-install-recommends nodejs

ENV JULIA_VERSION=1.4.1

RUN mkdir /opt/julia-${JULIA_VERSION} && \
    cd /tmp && \
    wget -q https://julialang-s3.julialang.org/bin/linux/x64/`echo ${JULIA_VERSION} | cut -d. -f 1,2`/julia-${JULIA_VERSION}-linux-x86_64.tar.gz && \
    tar xzf julia-${JULIA_VERSION}-linux-x86_64.tar.gz -C /opt/julia-${JULIA_VERSION} --strip-components=1 && \
    rm /tmp/julia-${JULIA_VERSION}-linux-x86_64.tar.gz

RUN ln -fs /opt/julia-*/bin/julia /usr/local/bin/julia

RUN julia -e 'import Pkg; Pkg.update()' && \
    julia -e 'import Pkg; Pkg.add("JSON")' && \
    julia -e 'import Pkg; Pkg.add("DiffEqBiological")' && \
    julia -e 'import Pkg; Pkg.add("DifferentialEquations")' && \
    julia -e 'import Pkg; Pkg.add("Plots")' && \
    julia -e 'import Pkg; Pkg.add("DiffEqMonteCarlo")'

ARG SBT_VERSION=1.3.13

RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

RUN \ 
  python2 --version && \
  python3 --version && \
  npm --version && \
  node --version

RUN pip2 install wheel
RUN pip2 install numpy
RUN pip2 install pysces
RUN pip3 install scipy matplotlib

RUN mkdir /AMIDOL
COPY . /AMIDOL

EXPOSE 8080

WORKDIR /AMIDOL

RUN sbt compile

CMD ["sbt", "run"]