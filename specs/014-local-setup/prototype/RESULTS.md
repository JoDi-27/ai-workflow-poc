# Spike 014 prototype results

Run 2026-10-27 on the owner's Mac: Docker Desktop 4.97.0 (Engine 29.9.2), Kubernetes in kind mode, one node, Kubernetes v1.36.1, CNI kindnet `v20260528-9350166c` with its `kube-network-policies` controller.

## NetworkPolicy enforcement

Manifests: `netpol-test.yaml` (namespaces `awf-runner` and `awf-sandbox`, an nginx stand-in for the Maven proxy, a `check` pod and an `evaluator` pod running curl), then `netpol-policies.yaml` (default-deny egress, allow DNS and the proxy for every sandbox pod, allow `160.79.104.0/23:443` for evaluator pods). Each probe is `curl --max-time 6`; `000` means the connection did not complete.

| From | To | Before policies | After policies |
| --- | --- | --- | --- |
| check | `http://maven-proxy.awf-runner` | 200 | 200 |
| check | `https://example.com` | 200 | blocked |
| check | `https://api.anthropic.com` | 404 | blocked |
| evaluator | `http://maven-proxy.awf-runner` | 200 | 200 |
| evaluator | `https://api.anthropic.com` | 404 | 404 (reached) |
| evaluator | `https://example.com` | 200 | blocked |

`api.anthropic.com` resolved to `160.79.104.10`, inside the published range. Result: kind mode enforces NetworkPolicy, including namespace selectors and `ipBlock`.

## Repeated Job name

`job-already-exists.yaml` created twice: the first create succeeds, the second fails with `Error from server (AlreadyExists): jobs.batch "rj-0001" already exists`. Result: a Job name derived from the runner job ID makes a repeated dispatch detectable. fabric8 surfaces this as HTTP 409; that mapping is checked in the scaffolding's runner tasks.

## Cluster start problems found on the way

- Kubernetes 1.37.0 does not start in kind mode on Docker Desktop 4.75.0 or 4.97.0: the generated kubeadm config is `v1beta3`, which kubeadm 1.37 rejects. 1.36.1 starts.
- The first pull of `kindest/node:v1.36.1` timed out (TLS handshake to `auth.docker.io`) and then took over an hour through Docker Desktop's VM network, while the host downloaded at about 11 MB/s. Pod images go through Docker Desktop's registry mirror (`http://registry-mirror:1273`).
