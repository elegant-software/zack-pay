const { readFileSync } = require("node:fs");

module.exports = async function publishRelease({ github, context, core }) {
  const plan = JSON.parse(readFileSync("release-output/release-plan.json", "utf8"));
  const releaseNotesMarkdown = readFileSync("release-output/release-notes.md", "utf8").trim();
  if (!plan.releaseRequired || !plan.nextVersion || !plan.targetSha) {
    throw new Error("The downloaded release plan is incomplete or does not require publication.");
  }

  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const tagName = `v${plan.nextVersion}`;

  let existingTag;
  try {
    const response = await github.rest.git.getRef({ owner, repo, ref: `tags/${tagName}` });
    existingTag = response.data;
  } catch (error) {
    if (error.status !== 404) throw error;
  }

  if (existingTag && existingTag.object.sha !== plan.targetSha) {
    throw new Error(`Tag ${tagName} already exists at a different commit than the release plan target.`);
  }

  if (!existingTag) {
    await github.rest.git.createRef({
      owner,
      repo,
      ref: `refs/tags/${tagName}`,
      sha: plan.targetSha,
    });
  }

  try {
    await github.rest.repos.createRelease({
      owner,
      repo,
      tag_name: tagName,
      name: tagName,
      body: releaseNotesMarkdown || "No release notes generated.",
      generate_release_notes: false,
    });
    core.info(`Created release ${tagName}.`);
  } catch (error) {
    if (error.status === 422) {
      core.info(`Release ${tagName} already exists. Skipping creation.`);
      return;
    }
    throw error;
  }
};
